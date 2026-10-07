package com.fixit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import com.fixit.entity.Question;
import com.fixit.entity.User;
import com.fixit.repository.QuestionRepository;
import com.fixit.repository.UserRepository;

/** Checkpoint 6: question CRUD and ownership. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class QuestionApiTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired QuestionRepository questions;

    RequestPostProcessor alice;
    RequestPostProcessor bob;

    @BeforeEach
    void setUp() {
        Authentication a = TestAuth.as(users.save(new User("alice@smail.iitm.ac.in")));
        Authentication b = TestAuth.as(users.save(new User("bob@smail.iitm.ac.in")));
        alice = authentication(a);
        bob = authentication(b);
    }

    private static String json(String title, String body) {
        return "{\"title\":\"" + title + "\",\"body\":\"" + body + "\",\"tags\":[\"tech\"]}";
    }

    private long createAsAlice() throws Exception {
        String res = mvc.perform(post("/api/questions").with(alice).contentType("application/json")
                .content(json("WiFi drops", "Disconnects often")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return new com.fasterxml.jackson.databind.ObjectMapper().readTree(res).get("questionId").asLong();
    }

    @Test
    void authorCreatesGetsAndUpdatesQuestion() throws Exception {
        long id = createAsAlice();

        Question stored = questions.findById(id).orElseThrow();
        assertThat(stored.getTitle()).isEqualTo("WiFi drops");

        mvc.perform(get("/api/questions/" + id).with(alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("WiFi drops"))
                .andExpect(jsonPath("$.isResolved").value(false))
                .andExpect(jsonPath("$.interestCount").value(0))
                .andExpect(jsonPath("$.acceptedAnswerId").doesNotExist());

        mvc.perform(put("/api/questions/" + id).with(alice).contentType("application/json")
                .content(json("WiFi drops in hostel", "Every hour")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("WiFi drops in hostel"))
                .andExpect(jsonPath("$.body").value("Every hour"));

        assertThat(questions.findById(id).orElseThrow().getBody()).isEqualTo("Every hour");
    }

    @Test
    void anyAuthenticatedUserCanReadQuestion() throws Exception {
        long id = createAsAlice();
        mvc.perform(get("/api/questions/" + id).with(bob)).andExpect(status().isOk());
    }

    @Test
    void otherUserCannotEdit() throws Exception {
        long id = createAsAlice();
        mvc.perform(put("/api/questions/" + id).with(bob).contentType("application/json")
                .content(json("Hijacked", "Hijacked")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
        assertThat(questions.findById(id).orElseThrow().getTitle()).isEqualTo("WiFi drops");
    }

    @Test
    void missingQuestionIs404() throws Exception {
        mvc.perform(get("/api/questions/999999").with(alice)).andExpect(status().isNotFound());
        mvc.perform(put("/api/questions/999999").with(alice).contentType("application/json").content(json("t", "b")))
                .andExpect(status().isNotFound());
    }

    @Test
    void invalidDataIs400() throws Exception {
        mvc.perform(post("/api/questions").with(alice).contentType("application/json").content(json(" ", "body")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("title")));
        mvc.perform(post("/api/questions").with(alice).contentType("application/json").content(json("t", "")))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/questions").with(alice).contentType("application/json").content(json("x".repeat(256), "b")))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/questions").with(alice).contentType("application/json").content("not json"))
                .andExpect(status().isBadRequest());
        assertThat(questions.count()).isZero();
    }

    @Test
    void unauthenticatedIs401() throws Exception {
        mvc.perform(post("/api/questions").contentType("application/json").content(json("t", "b")))
                .andExpect(status().isUnauthorized());
    }
}
