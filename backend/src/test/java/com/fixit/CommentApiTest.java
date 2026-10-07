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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fixit.entity.User;
import com.fixit.repository.UserRepository;

import jakarta.persistence.EntityManager;

/** Checkpoint 10: comments and replies on a question. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CommentApiTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired EntityManager em;
    @Autowired JdbcTemplate jdbc;

    RequestPostProcessor alice, bob;
    long questionId;

    @BeforeEach
    void setUp() throws Exception {
        alice = authentication(TestAuth.as(users.save(new User("alice@smail.iitm.ac.in"))));
        bob = authentication(TestAuth.as(users.save(new User("bob@smail.iitm.ac.in"))));
        questionId = create("/api/questions", alice, "{\"title\":\"t\",\"body\":\"b\",\"tags\":[\"tech\"]}", "questionId");
    }

    private long create(String url, RequestPostProcessor as, String json, String idField) throws Exception {
        String res = mvc.perform(post(url).with(as).contentType("application/json").content(json))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return new ObjectMapper().readTree(res).get(idField).asLong();
    }

    private long comment(RequestPostProcessor as, String body) throws Exception {
        return create("/api/questions/" + questionId + "/comments", as, "{\"body\":\"" + body + "\"}", "commentId");
    }

    private long reply(RequestPostProcessor as, long parent, String body) throws Exception {
        return create("/api/comments/" + parent + "/replies", as, "{\"body\":\"" + body + "\"}", "commentId");
    }

    @Test
    void nestedDiscussionIsRetrievedCorrectly() throws Exception {
        long a = comment(bob, "Comment A");
        long a1 = reply(alice, a, "Reply A1");
        reply(bob, a, "Reply A2");
        reply(alice, a1, "Reply to A1");   // deeper nesting
        comment(alice, "Comment B");

        mvc.perform(get("/api/questions/" + questionId + "/comments").with(bob))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].body").value("Comment A"))
                .andExpect(jsonPath("$[0].parentCommentId").doesNotExist())
                .andExpect(jsonPath("$[0].replies.length()").value(2))
                .andExpect(jsonPath("$[0].replies[0].body").value("Reply A1"))
                .andExpect(jsonPath("$[0].replies[0].parentCommentId").value(a))
                .andExpect(jsonPath("$[0].replies[0].replies[0].body").value("Reply to A1"))
                .andExpect(jsonPath("$[0].replies[1].body").value("Reply A2"))
                .andExpect(jsonPath("$[0].replies[1].replies.length()").value(0))
                .andExpect(jsonPath("$[1].body").value("Comment B"))
                .andExpect(jsonPath("$[1].replies.length()").value(0));

        em.flush(); // raw SQL: replies are stored with parent ids on the same question
        assertThat(jdbc.queryForObject("select count(*) from question_comments where question_id = ? "
                + "and parent_comment_id is not null", Integer.class, questionId)).isEqualTo(3);
    }

    @Test
    void commentsOfOneQuestionStayOnThatQuestion() throws Exception {
        long other = create("/api/questions", alice, "{\"title\":\"t2\",\"body\":\"b\",\"tags\":[\"tech\"]}", "questionId");
        long c = comment(bob, "on first question");
        long r = reply(alice, c, "reply");
        mvc.perform(get("/api/questions/" + other + "/comments").with(alice)).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/questions/" + questionId + "/comments").with(alice))
                .andExpect(jsonPath("$[0].replies[0].questionId").value(questionId));
        assertThat(r).isGreaterThan(c);
    }

    @Test
    void authorCanEditOwnComment_othersCannot() throws Exception {
        long c = comment(bob, "old");
        mvc.perform(put("/api/comments/" + c).with(bob).contentType("application/json").content("{\"body\":\"new\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.body").value("new"));
        em.flush();
        assertThat(jdbc.queryForObject("select body from question_comments where comment_id = ?", String.class, c))
                .isEqualTo("new");

        // question author is "another user" for this comment
        mvc.perform(put("/api/comments/" + c).with(alice).contentType("application/json").content("{\"body\":\"hijack\"}"))
                .andExpect(status().isForbidden());
        em.flush();
        assertThat(jdbc.queryForObject("select body from question_comments where comment_id = ?", String.class, c))
                .isEqualTo("new");
    }

    @Test
    void repliesCanBeEditedOnlyByTheirAuthorToo() throws Exception {
        long c = comment(bob, "c");
        long r = reply(alice, c, "old reply");
        mvc.perform(put("/api/comments/" + r).with(bob).contentType("application/json").content("{\"body\":\"x\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/comments/" + r).with(alice).contentType("application/json").content("{\"body\":\"edited\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.parentCommentId").value(c));
    }

    @Test
    void invalidMissingAndUnauthenticated() throws Exception {
        mvc.perform(post("/api/questions/" + questionId + "/comments").with(bob).contentType("application/json")
                .content("{\"body\":\"  \"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/questions/" + questionId + "/comments").with(bob).contentType("application/json")
                .content("{\"body\":\"" + "x".repeat(5001) + "\"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/questions/999999/comments").with(bob).contentType("application/json")
                .content("{\"body\":\"x\"}")).andExpect(status().isNotFound());
        mvc.perform(post("/api/comments/999999/replies").with(bob).contentType("application/json")
                .content("{\"body\":\"x\"}")).andExpect(status().isNotFound());
        mvc.perform(put("/api/comments/999999").with(bob).contentType("application/json")
                .content("{\"body\":\"x\"}")).andExpect(status().isNotFound());
        mvc.perform(get("/api/questions/999999/comments").with(bob)).andExpect(status().isNotFound());
        mvc.perform(post("/api/questions/" + questionId + "/comments").contentType("application/json")
                .content("{\"body\":\"x\"}")).andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("select count(*) from question_comments", Integer.class)).isZero();
    }

    @Test
    void thereIsNoDeleteOperation() throws Exception {
        long c = comment(bob, "permanent");
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/comments/" + c).with(bob))
                .andExpect(status().is4xxClientError());
        mvc.perform(get("/api/questions/" + questionId + "/comments").with(bob)).andExpect(jsonPath("$.length()").value(1));
    }
}
