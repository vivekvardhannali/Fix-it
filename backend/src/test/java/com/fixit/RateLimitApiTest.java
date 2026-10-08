package com.fixit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fixit.entity.User;
import com.fixit.repository.UserRepository;
import com.fixit.security.RateLimiter;

import jakarta.persistence.EntityManager;

/** The limits that protect the paid embedding calls and sign-up, with small limits so the tests stay quick. */
@SpringBootTest(properties = { "app.rate-limit.search-per-minute=3", "app.rate-limit.question-writes-per-minute=2",
        "app.rate-limit.registrations-per-ip-per-hour=2" })
@AutoConfigureMockMvc
@Transactional
class RateLimitApiTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired RateLimiter rateLimiter;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;
    @Autowired ObjectMapper mapper;

    RequestPostProcessor alice, bob;

    @BeforeEach
    void setUp() {
        rateLimiter.clear();    // singleton shared by the tests of this context (registrations come from one address)
        alice = authentication(TestAuth.as(users.save(new User("alice@smail.iitm.ac.in"))));
        bob = authentication(TestAuth.as(users.save(new User("bob@smail.iitm.ac.in"))));
    }

    private int search(RequestPostProcessor as) throws Exception {
        return mvc.perform(get("/api/search").param("q", "wifi hostel").param("mode", "lexical").with(as)).andReturn().getResponse().getStatus();
    }

    private static final String QUESTION = "{\"title\":\"t\",\"body\":\"b\",\"tags\":[\"tech\"]}";

    @Test
    void searchIsLimitedPerUser_with429AndRetryAfter() throws Exception {
        for (int i = 0; i < 3; i++) assertThat(search(alice)).isEqualTo(200);
        mvc.perform(get("/api/search").param("q", "wifi").param("mode", "lexical").with(alice))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.message").value(containsString("Too many requests. Try again in")));
        assertThat(search(bob)).isEqualTo(200);                       // another user is unaffected
    }

    @Test
    void invalidSearchesCountToo_soTheLimitCannotBeProbedForFree() throws Exception {
        for (int i = 0; i < 3; i++) {
            mvc.perform(get("/api/search").param("q", " ").with(alice)).andExpect(status().isBadRequest());
        }
        assertThat(search(alice)).isEqualTo(429);
    }

    @Test
    void creatingAndEditingQuestionsAreLimitedTogether_andNothingIsSavedOverTheLimit() throws Exception {
        String res = mvc.perform(post("/api/questions").with(alice).contentType("application/json").content(QUESTION))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = mapper.readTree(res).get("questionId").asLong();
        mvc.perform(put("/api/questions/" + id).with(alice).contentType("application/json").content(QUESTION)).andExpect(status().isOk());

        mvc.perform(post("/api/questions").with(alice).contentType("application/json").content(QUESTION))
                .andExpect(status().isTooManyRequests()).andExpect(header().exists("Retry-After"));
        mvc.perform(put("/api/questions/" + id).with(alice).contentType("application/json").content(QUESTION))
                .andExpect(status().isTooManyRequests());
        em.flush();
        assertThat(jdbc.queryForObject("select count(*) from questions where author_id = ?", Integer.class, users.findBySmail("alice@smail.iitm.ac.in").orElseThrow().getId()))
                .isEqualTo(1);
        // bob can still post; alice can still do everything that does not cost an embedding call
        mvc.perform(post("/api/questions").with(bob).contentType("application/json").content(QUESTION)).andExpect(status().isCreated());
        mvc.perform(post("/api/questions/" + id + "/answers").with(alice).contentType("application/json").content("{\"body\":\"x\"}")).andExpect(status().isCreated());
        mvc.perform(post("/api/questions/" + id + "/comments").with(alice).contentType("application/json").content("{\"body\":\"x\"}")).andExpect(status().isCreated());
        mvc.perform(post("/api/questions/" + id + "/interest").with(alice)).andExpect(status().isOk());
        mvc.perform(get("/api/questions/" + id).with(alice)).andExpect(status().isOk());
        mvc.perform(get("/api/questions").with(alice)).andExpect(status().isOk());
    }

    @Test
    void signUpIsLimitedPerAddress_evenWhenTheRequestsAreInvalid() throws Exception {
        String bad = "{\"username\":\"x\",\"smail\":\"a@gmail.com\",\"password\":\"short\"}";
        mvc.perform(post("/api/auth/register").contentType("application/json").content(bad)).andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/register").contentType("application/json").content(bad)).andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/register").contentType("application/json")
                .content("{\"username\":\"valid_user\",\"smail\":\"valid@smail.iitm.ac.in\",\"password\":\"correct horse battery\"}"))
                .andExpect(status().isTooManyRequests()).andExpect(header().exists("Retry-After"));
        em.flush();
        assertThat(jdbc.queryForObject("select count(*) from users where username = 'valid_user'", Integer.class)).isZero();
    }

    @Test
    void loggingInIsNotCountedAsASignUp() throws Exception {
        for (int i = 0; i < 4; i++) {
            mvc.perform(post("/api/auth/login").contentType("application/json").content("{\"identifier\":\"nobody\",\"password\":\"x" + i + "\"}"))
                    .andExpect(i < 4 ? status().isUnauthorized() : status().isTooManyRequests());
        }
    }
}
