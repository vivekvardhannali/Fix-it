package com.fixit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
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

/** Checkpoint 13. A owns the question; B and C are other users. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class NotificationApiTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired EntityManager em;
    @Autowired JdbcTemplate jdbc;

    RequestPostProcessor userA, userB, userC;
    long idA, idB, idC, questionId;

    @BeforeEach
    void setUp() throws Exception {
        User a = users.save(new User("a@smail.iitm.ac.in"));
        User b = users.save(new User("b@smail.iitm.ac.in"));
        User c = users.save(new User("c@smail.iitm.ac.in"));
        idA = a.getId(); idB = b.getId(); idC = c.getId();
        userA = authentication(TestAuth.as(a));
        userB = authentication(TestAuth.as(b));
        userC = authentication(TestAuth.as(c));
        questionId = create("/api/questions", userA, "{\"title\":\"t\",\"body\":\"b\",\"tags\":[\"tech\"]}", "questionId");
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

    private List<Map<String, Object>> dbNotifications(long userId) {
        em.flush();
        return jdbc.queryForList("select type, question_id, comment_id, is_read from notifications "
                + "where user_id = ? order by notification_id", userId);
    }

    @Test
    void commentOnMyQuestion_notifiesTheQuestionAuthor_andOpensTheRightComment() throws Exception {
        long c = comment(userB, "Try DNS");

        mvc.perform(get("/api/notifications").with(userA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].type").value("COMMENT_ON_QUESTION"))
                .andExpect(jsonPath("$[0].questionId").value(questionId))
                .andExpect(jsonPath("$[0].commentId").value(c))
                .andExpect(jsonPath("$[0].actorId").value(idB))
                .andExpect(jsonPath("$[0].isRead").value(false));
        mvc.perform(get("/api/notifications").with(userB)).andExpect(jsonPath("$.length()").value(0));

        List<Map<String, Object>> rows = dbNotifications(idA);   // and it is really stored
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("type")).isEqualTo("COMMENT_ON_QUESTION");
        assertThat(((Number) rows.get(0).get("comment_id")).longValue()).isEqualTo(c);
    }

    @Test
    void replyToMyComment_notifiesTheCommentAuthor() throws Exception {
        long c = comment(userB, "B's comment");
        long r = reply(userA, c, "A replies");           // A answers B

        mvc.perform(get("/api/notifications").with(userB))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].type").value("REPLY_TO_COMMENT"))
                .andExpect(jsonPath("$[0].commentId").value(r))
                .andExpect(jsonPath("$[0].actorId").value(idA))
                .andExpect(jsonPath("$[0].questionId").value(questionId));
        // A got one notification from B's top-level comment, but none for its own reply
        assertThat(dbNotifications(idA)).hasSize(1);
    }

    @Test
    void thirdPartyReply_notifiesOnlyTheParentCommentAuthor() throws Exception {
        long c = comment(userB, "B's comment");           // A notified (1)
        reply(userC, c, "C replies to B");                // B notified; A not again
        assertThat(dbNotifications(idB)).hasSize(1);
        assertThat(dbNotifications(idA)).hasSize(1);
        assertThat(dbNotifications(idC)).isEmpty();
    }

    @Test
    void neverNotifyAboutYourOwnActions() throws Exception {
        long mine = comment(userA, "my own question, my own comment");
        reply(userA, mine, "replying to myself");
        assertThat(dbNotifications(idA)).isEmpty();
    }

    @Test
    void markReadAndUnreadCount() throws Exception {
        comment(userB, "one");
        comment(userC, "two");
        mvc.perform(get("/api/notifications/unread-count").with(userA)).andExpect(jsonPath("$.unreadCount").value(2));
        mvc.perform(get("/api/notifications").with(userA))     // newest first
                .andExpect(jsonPath("$[0].actorId").value(idC)).andExpect(jsonPath("$[1].actorId").value(idB));

        long first = new ObjectMapper().readTree(mvc.perform(get("/api/notifications").with(userA))
                .andReturn().getResponse().getContentAsString()).get(0).get("notificationId").asLong();
        mvc.perform(put("/api/notifications/" + first + "/read").with(userA))
                .andExpect(status().isOk()).andExpect(jsonPath("$.isRead").value(true));
        mvc.perform(put("/api/notifications/" + first + "/read").with(userA)).andExpect(status().isOk()); // idempotent

        mvc.perform(get("/api/notifications/unread-count").with(userA)).andExpect(jsonPath("$.unreadCount").value(1));
        mvc.perform(get("/api/notifications?unreadOnly=true").with(userA))
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].actorId").value(idB));
        mvc.perform(get("/api/notifications?limit=1").with(userA)).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/notifications?limit=0").with(userA)).andExpect(jsonPath("$.length()").value(1)); // clamped to 1
        assertThat(dbNotifications(idA).stream().filter(r -> Boolean.TRUE.equals(r.get("is_read")))).hasSize(1);
    }

    @Test
    void cannotMarkSomeoneElsesNotificationRead() throws Exception {
        comment(userB, "hi");
        long n = new ObjectMapper().readTree(mvc.perform(get("/api/notifications").with(userA))
                .andReturn().getResponse().getContentAsString()).get(0).get("notificationId").asLong();
        mvc.perform(put("/api/notifications/" + n + "/read").with(userC)).andExpect(status().isForbidden());
        assertThat(dbNotifications(idA).get(0).get("is_read")).isEqualTo(false);
        mvc.perform(put("/api/notifications/999999/read").with(userA)).andExpect(status().isNotFound());
    }

    @Test
    void otherActionsDoNotNotify_andAuthIsRequired() throws Exception {
        long c = comment(userA, "A's comment");           // own comment: nothing
        mvc.perform(put("/api/comments/" + c + "/vote").with(userB).contentType("application/json")
                .content("{\"voteType\":\"UP\"}")).andExpect(status().isOk());
        mvc.perform(post("/api/questions/" + questionId + "/interest").with(userB)).andExpect(status().isOk());
        mvc.perform(post("/api/questions/" + questionId + "/answers").with(userB).contentType("application/json")
                .content("{\"body\":\"answer\"}")).andExpect(status().isCreated());
        assertThat(dbNotifications(idA)).isEmpty();

        mvc.perform(get("/api/notifications")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/notifications/unread-count")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/notifications/1/read")).andExpect(status().isUnauthorized());
    }
}
