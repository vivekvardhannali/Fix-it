package com.fixit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fixit.entity.User;
import com.fixit.repository.UserRepository;

import jakarta.persistence.EntityManager;

/** Checkpoint 12: the "+" (interest) feature. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class InterestApiTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired EntityManager em;
    @Autowired JdbcTemplate jdbc;

    RequestPostProcessor alice, bob, carol;
    long questionId;

    @BeforeEach
    void setUp() throws Exception {
        alice = authentication(TestAuth.as(users.save(new User("alice@smail.iitm.ac.in"))));
        bob = authentication(TestAuth.as(users.save(new User("bob@smail.iitm.ac.in"))));
        carol = authentication(TestAuth.as(users.save(new User("carol@smail.iitm.ac.in"))));
        String res = mvc.perform(post("/api/questions").with(alice).contentType("application/json")
                .content("{\"title\":\"t\",\"body\":\"b\",\"tags\":[\"tech\"]}")).andReturn().getResponse().getContentAsString();
        questionId = new ObjectMapper().readTree(res).get("questionId").asLong();
    }

    private ResultActions plus(RequestPostProcessor as) throws Exception {
        return mvc.perform(post("/api/questions/" + questionId + "/interest").with(as)).andExpect(status().isOk());
    }

    /** Counter column vs. actual rows, straight from the DB. */
    private void assertDb(int expectedCount) {
        em.flush();
        assertThat(jdbc.queryForObject("select interest_count from questions where question_id = ?", Integer.class, questionId))
                .isEqualTo(expectedCount);
        assertThat(jdbc.queryForObject("select count(*) from question_interests where question_id = ?", Integer.class, questionId))
                .isEqualTo(expectedCount);
    }

    @Test
    void addThenRemove() throws Exception {
        plus(bob).andExpect(jsonPath("$.interested").value(true)).andExpect(jsonPath("$.interestCount").value(1));
        assertDb(1);
        plus(bob).andExpect(jsonPath("$.interested").value(false)).andExpect(jsonPath("$.interestCount").value(0));
        assertDb(0);
        plus(bob).andExpect(jsonPath("$.interestCount").value(1)); // and can come back
        assertDb(1);
    }

    @Test
    void sameUserNeverHasDuplicateInterest() throws Exception {
        for (int i = 0; i < 5; i++) {
            plus(bob);
        }
        // 5 clicks = odd number -> interested, exactly one row
        assertDb(1);
    }

    @Test
    void differentUsersAreIndependent() throws Exception {
        plus(alice);   // the question's author may also mark it
        plus(bob);
        plus(carol).andExpect(jsonPath("$.interestCount").value(3));
        assertDb(3);
        plus(bob).andExpect(jsonPath("$.interestCount").value(2));
        assertDb(2);

        mvc.perform(get("/api/questions/" + questionId + "/interest").with(bob))
                .andExpect(jsonPath("$.interested").value(false)).andExpect(jsonPath("$.interestCount").value(2));
        mvc.perform(get("/api/questions/" + questionId + "/interest").with(carol))
                .andExpect(jsonPath("$.interested").value(true));
        mvc.perform(get("/api/questions/" + questionId).with(carol)).andExpect(jsonPath("$.interestCount").value(2));
    }

    @Test
    void doesNotChangeQuestionUpdatedAt() throws Exception {
        em.flush();
        Object before = jdbc.queryForObject("select updated_at from questions where question_id = ?", Object.class, questionId);
        plus(bob);
        em.flush();
        assertThat(jdbc.queryForObject("select updated_at from questions where question_id = ?", Object.class, questionId))
                .isEqualTo(before);
    }

    @Test
    void missingAndUnauthenticated() throws Exception {
        mvc.perform(post("/api/questions/999999/interest").with(bob)).andExpect(status().isNotFound());
        mvc.perform(get("/api/questions/999999/interest").with(bob)).andExpect(status().isNotFound());
        mvc.perform(post("/api/questions/" + questionId + "/interest")).andExpect(status().isUnauthorized());
        assertDb(0);
    }
}
