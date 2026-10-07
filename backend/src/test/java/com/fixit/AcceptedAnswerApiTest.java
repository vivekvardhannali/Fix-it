package com.fixit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fixit.entity.User;
import com.fixit.repository.UserRepository;

import jakarta.persistence.EntityManager;

/** Checkpoint 9: accepted answer / resolution. A = question author, B = answerer, C = bystander. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AcceptedAnswerApiTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired EntityManager em;
    @Autowired JdbcTemplate jdbc;

    RequestPostProcessor userA, userB, userC;
    long questionId, answerId;

    @BeforeEach
    void setUp() throws Exception {
        userA = authentication(TestAuth.as(users.save(new User("a@smail.iitm.ac.in"))));
        userB = authentication(TestAuth.as(users.save(new User("b@smail.iitm.ac.in"))));
        userC = authentication(TestAuth.as(users.save(new User("c@smail.iitm.ac.in"))));
        questionId = create("/api/questions", userA, "{\"title\":\"t\",\"body\":\"b\",\"tags\":[\"tech\"]}", "questionId");
        answerId = create("/api/questions/" + questionId + "/answers", userB, "{\"body\":\"fix\"}", "answerId");
    }

    private long create(String url, RequestPostProcessor as, String json, String idField) throws Exception {
        String res = mvc.perform(post(url).with(as).contentType("application/json").content(json))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return new ObjectMapper().readTree(res).get(idField).asLong();
    }

    private String accept(long answer) {
        return "{\"answerId\":" + answer + "}";
    }

    private Map<String, Object> dbRow() {
        em.flush();
        return jdbc.queryForMap("select accepted_answer_id, is_resolved from questions where question_id = ?", questionId);
    }

    @Test
    void authorSelectsAnswer_questionBecomesResolved() throws Exception {
        assertThat(dbRow().get("is_resolved")).isEqualTo(false);

        mvc.perform(put("/api/questions/" + questionId + "/accepted-answer").with(userA)
                .contentType("application/json").content(accept(answerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isResolved").value(true))
                .andExpect(jsonPath("$.acceptedAnswerId").value(answerId));

        Map<String, Object> row = dbRow(); // verified in the DB itself
        assertThat(((Number) row.get("accepted_answer_id")).longValue()).isEqualTo(answerId);
        assertThat(row.get("is_resolved")).isEqualTo(true);

        mvc.perform(get("/api/questions/" + questionId + "/answers").with(userC))
                .andExpect(jsonPath("$[0].isAccepted").value(true));
    }

    @Test
    void authorCanSwitchToAnotherAnswer() throws Exception {
        long second = create("/api/questions/" + questionId + "/answers", userC, "{\"body\":\"better\"}", "answerId");
        mvc.perform(put("/api/questions/" + questionId + "/accepted-answer").with(userA)
                .contentType("application/json").content(accept(answerId))).andExpect(status().isOk());
        mvc.perform(put("/api/questions/" + questionId + "/accepted-answer").with(userA)
                .contentType("application/json").content(accept(second))).andExpect(status().isOk());
        assertThat(((Number) dbRow().get("accepted_answer_id")).longValue()).isEqualTo(second);
        mvc.perform(get("/api/questions/" + questionId + "/answers").with(userA))
                .andExpect(jsonPath("$[0].isAccepted").value(false))
                .andExpect(jsonPath("$[1].isAccepted").value(true));
    }

    @Test
    void otherUsersCannotSelect_includingTheAnswerAuthor() throws Exception {
        for (RequestPostProcessor other : new RequestPostProcessor[] { userB, userC }) {
            mvc.perform(put("/api/questions/" + questionId + "/accepted-answer").with(other)
                    .contentType("application/json").content(accept(answerId)))
                    .andExpect(status().isForbidden());
        }
        Map<String, Object> row = dbRow();
        assertThat(row.get("accepted_answer_id")).isNull();
        assertThat(row.get("is_resolved")).isEqualTo(false);
    }

    @Test
    void answerFromAnotherQuestionIsRejected() throws Exception {
        long otherQuestion = create("/api/questions", userA, "{\"title\":\"t2\",\"body\":\"b\",\"tags\":[\"tech\"]}", "questionId");
        long foreignAnswer = create("/api/questions/" + otherQuestion + "/answers", userB, "{\"body\":\"x\"}", "answerId");
        mvc.perform(put("/api/questions/" + questionId + "/accepted-answer").with(userA)
                .contentType("application/json").content(accept(foreignAnswer)))
                .andExpect(status().isBadRequest());
        assertThat(dbRow().get("accepted_answer_id")).isNull();
    }

    @Test
    void invalidRequests() throws Exception {
        mvc.perform(put("/api/questions/" + questionId + "/accepted-answer").with(userA)
                .contentType("application/json").content(accept(999999))).andExpect(status().isNotFound());
        mvc.perform(put("/api/questions/999999/accepted-answer").with(userA)
                .contentType("application/json").content(accept(answerId))).andExpect(status().isNotFound());
        mvc.perform(put("/api/questions/" + questionId + "/accepted-answer").with(userA)
                .contentType("application/json").content("{}")).andExpect(status().isBadRequest());
        mvc.perform(put("/api/questions/" + questionId + "/accepted-answer")
                .contentType("application/json").content(accept(answerId))).andExpect(status().isUnauthorized());
    }
}
