package com.fixit;

import static org.assertj.core.api.Assertions.assertThat;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fixit.entity.User;
import com.fixit.repository.AnswerRepository;
import com.fixit.repository.UserRepository;

/** Checkpoint 8: answers. A = question author, B = answerer. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AnswerApiTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired AnswerRepository answers;

    RequestPostProcessor userA;
    RequestPostProcessor userB;
    long questionId;

    @BeforeEach
    void setUp() throws Exception {
        userA = authentication(TestAuth.as(users.save(new User("a@smail.iitm.ac.in"))));
        userB = authentication(TestAuth.as(users.save(new User("b@smail.iitm.ac.in"))));
        questionId = idOf(mvc.perform(post("/api/questions").with(userA).contentType("application/json")
                .content("{\"title\":\"t\",\"body\":\"b\",\"tags\":[\"tech\"]}")).andReturn().getResponse().getContentAsString(), "questionId");
    }

    private static long idOf(String json, String field) throws Exception {
        return new ObjectMapper().readTree(json).get(field).asLong();
    }

    private long answer(RequestPostProcessor as, String body) throws Exception {
        return idOf(mvc.perform(post("/api/questions/" + questionId + "/answers").with(as)
                .contentType("application/json").content("{\"body\":\"" + body + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "answerId");
    }

    @Test
    void userBAnswersAndAnswerAppears() throws Exception {
        answer(userB, "Restart the router");
        mvc.perform(get("/api/questions/" + questionId + "/answers").with(userA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].body").value("Restart the router"))
                .andExpect(jsonPath("$[0].questionId").value(questionId))
                .andExpect(jsonPath("$[0].isAccepted").value(false));
    }

    @Test
    void multipleAnswersListedInCreationOrder() throws Exception {
        answer(userB, "first");
        answer(userA, "second");
        mvc.perform(get("/api/questions/" + questionId + "/answers").with(userA))
                .andExpect(jsonPath("$[0].body").value("first"))
                .andExpect(jsonPath("$[1].body").value("second"));
    }

    @Test
    void answerAuthorCanEdit_questionAuthorCannot() throws Exception {
        long id = answer(userB, "old");
        mvc.perform(put("/api/answers/" + id).with(userB).contentType("application/json").content("{\"body\":\"new\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.body").value("new"));
        assertThat(answers.findById(id).orElseThrow().getBody()).isEqualTo("new");

        mvc.perform(put("/api/answers/" + id).with(userA).contentType("application/json").content("{\"body\":\"hijack\"}"))
                .andExpect(status().isForbidden());
        assertThat(answers.findById(id).orElseThrow().getBody()).isEqualTo("new");
    }

    @Test
    void invalidAndMissing() throws Exception {
        mvc.perform(post("/api/questions/" + questionId + "/answers").with(userB).contentType("application/json")
                .content("{\"body\":\" \"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/questions/999999/answers").with(userB).contentType("application/json")
                .content("{\"body\":\"x\"}")).andExpect(status().isNotFound());
        mvc.perform(get("/api/questions/999999/answers").with(userB)).andExpect(status().isNotFound());
        mvc.perform(put("/api/answers/999999").with(userB).contentType("application/json").content("{\"body\":\"x\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/questions/" + questionId + "/answers").contentType("application/json")
                .content("{\"body\":\"x\"}")).andExpect(status().isUnauthorized());
        assertThat(answers.count()).isZero();
    }
}
