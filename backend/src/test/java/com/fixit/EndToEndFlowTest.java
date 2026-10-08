package com.fixit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fixit.security.LoginThrottle;
import com.fixit.support.FakeEmbeddings;

/**
 * Checkpoint 23: the whole backend flow from the roadmap, without a frontend.
 *
 * NOT @Transactional on purpose: every request commits for real, which exercises what rolled-back tests cannot -
 * the after-commit embedding hook, tag replacement across separate requests, and the lexical index seeing committed
 * data. Cleans up after itself; if it ever dies midway, delete users like 'e2e-%@smail.iitm.ac.in' and their rows.
 *
 * Login is the real username/password flow over HTTP (register -> session cookie). The embedding provider is the test-only
 * fake, so this proves the flow, not search quality.
 */
@SpringBootTest(properties = { FakeEmbeddings.P_PROVIDER, FakeEmbeddings.P_MODEL, FakeEmbeddings.P_URL,
        FakeEmbeddings.P_KEY, FakeEmbeddings.P_DIM, FakeEmbeddings.P_THRESHOLD, FakeEmbeddings.P_LEXICAL_THRESHOLD,
        FakeEmbeddings.P_HYBRID_THRESHOLD, FakeEmbeddings.P_WEIGHT })
@Import(FakeEmbeddings.Config.class)
@AutoConfigureMockMvc
class EndToEndFlowTest {

    static final String USERS = "(select user_id from users where smail like 'e2e-%@smail.iitm.ac.in')";
    static final String QUESTIONS = "(select question_id from questions where author_id in " + USERS + ")";

    @Autowired MockMvc mvc;
    @Autowired LoginThrottle throttle;
    @Autowired com.fixit.security.RateLimiter rateLimiter;
    @Autowired FakeEmbeddings fake;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;

    @AfterEach
    void cleanUp() {
        jdbc.update("delete from notifications where user_id in " + USERS + " or question_id in " + QUESTIONS);
        jdbc.update("delete from question_comment_votes where user_id in " + USERS
                + " or comment_id in (select comment_id from question_comments where question_id in " + QUESTIONS + ")");
        jdbc.update("delete from question_comments where parent_comment_id is not null and question_id in " + QUESTIONS);
        jdbc.update("delete from question_comments where question_id in " + QUESTIONS);
        jdbc.update("delete from question_interests where user_id in " + USERS + " or question_id in " + QUESTIONS);
        jdbc.update("delete from question_embeddings where question_id in " + QUESTIONS);
        jdbc.update("delete from question_tags where question_id in " + QUESTIONS);
        jdbc.update("update questions set accepted_answer_id = null where question_id in " + QUESTIONS);
        jdbc.update("delete from answers where question_id in " + QUESTIONS + " or author_id in " + USERS);
        jdbc.update("delete from questions where question_id in " + QUESTIONS);
        jdbc.update("delete from tags where lower(name) like 'e2e%'");
        jdbc.update("delete from users where smail like 'e2e-%@smail.iitm.ac.in'");
    }

    // ---- helpers ------------------------------------------------------------------------------

    static final String PASSWORD = "correct horse battery";

    /** Signs a new user up over HTTP; the response starts a session, which later calls reuse (like a browser cookie). */
    private RequestPostProcessor register(String username, String smail) throws Exception {
        MvcResult result = mvc.perform(request(HttpMethod.POST, "/api/auth/register").contentType("application/json")
                .content("{\"username\":\"" + username + "\",\"smail\":\"" + smail + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andReturn();
        assertThat(result.getResponse().getStatus()).as("register %s -> %s", username, result.getResponse().getContentAsString()).isEqualTo(201);
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        assertThat(session).isNotNull();
        return request -> {
            request.setSession(session);
            return request;
        };
    }

    private JsonNode call(HttpMethod method, String url, RequestPostProcessor as, String body, int expectedStatus) throws Exception {
        var req = request(method, url);
        if (as != null) req.with(as);
        if (body != null) req.contentType("application/json").content(body);
        MockHttpServletResponse res = mvc.perform(req).andReturn().getResponse();
        assertThat(res.getStatus()).as("%s %s -> %s", method, url, res.getContentAsString()).isEqualTo(expectedStatus);
        String text = res.getContentAsString();
        return text.isEmpty() ? mapper.nullNode() : mapper.readTree(text);
    }

    private int count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    // ---- the flow -----------------------------------------------------------------------------

    @Test
    void theWholeBackendFlowWorks() throws Exception {
        fake.reset();

        // 1. Sign up / log in with username + password -> user created; bad attempts are refused
        throttle.clear();
        rateLimiter.clear();
        RequestPostProcessor alice = register("e2e_alice", "e2e-alice@smail.iitm.ac.in");
        RequestPostProcessor bob = register("e2e_bob", "e2e-bob@smail.iitm.ac.in");
        RequestPostProcessor carol = register("e2e_carol", "e2e-carol@smail.iitm.ac.in");
        assertThat(count("select count(*) from users where smail like 'e2e-%'")).isEqualTo(3);
        call(HttpMethod.POST, "/api/auth/register", null,
                "{\"username\":\"e2e_other\",\"smail\":\"E2E-Alice@smail.iitm.ac.in\",\"password\":\"" + PASSWORD + "\"}", 409);   // same smail
        call(HttpMethod.POST, "/api/auth/register", null,
                "{\"username\":\"e2e_mallory\",\"smail\":\"e2e-mallory@gmail.com\",\"password\":\"" + PASSWORD + "\"}", 400);      // not IITM
        assertThat(count("select count(*) from users where smail like 'e2e-mallory%' or username like 'e2e_mallory%' or username = 'e2e_other'")).isZero();
        call(HttpMethod.POST, "/api/auth/login", null, "{\"identifier\":\"e2e_alice\",\"password\":\"wrong password\"}", 401);
        call(HttpMethod.POST, "/api/auth/login", null, "{\"identifier\":\"e2e-alice@smail.iitm.ac.in\",\"password\":\"" + PASSWORD + "\"}", 200);  // by smail
        call(HttpMethod.POST, "/api/auth/login", null, "{\"identifier\":\"E2E_ALICE\",\"password\":\"" + PASSWORD + "\"}", 200);                  // by username

        JsonNode me = call(HttpMethod.GET, "/api/me", alice, null, 200);
        assertThat(me.get("smail").asText()).isEqualTo("e2e-alice@smail.iitm.ac.in");
        assertThat(me.get("username").asText()).isEqualTo("e2e_alice");
        assertThat(me.toString()).doesNotContain("$2").doesNotContainIgnoringCase("password");
        long aliceId = me.get("userId").asLong();
        call(HttpMethod.GET, "/api/me", null, null, 401);                           // no session -> rejected

        // 2. Create question + tags -> embedding generated and stored (after commit)
        JsonNode created = call(HttpMethod.POST, "/api/questions", alice,
                "{\"title\":\"Zephyrnet router keeps dropping connection in hostel\","
                        + "\"body\":\"my laptop disconnects from the zephyrnet router every hour\\nstarted last week\","
                        + "\"tags\":[\"tech\",\"others\"]}", 201);
        long questionId = created.get("questionId").asLong();
        assertThat(created.get("authorId").asLong()).isEqualTo(aliceId);
        assertThat(created.get("tags")).extracting(JsonNode::asText).containsExactly("others", "tech");
        assertThat(count("select vector_dims(embedding) from question_embeddings where question_id = ?", questionId))
                .isEqualTo(FakeEmbeddings.DIM);
        String vectorBefore = jdbc.queryForObject("select embedding::text from question_embeddings where question_id = ?", String.class, questionId);

        // tag replacement across separate, committed requests really changes the database
        call(HttpMethod.PUT, "/api/questions/" + questionId + "/tags", alice, "{\"tags\":[\"tech\",\"code\"]}", 200);
        assertThat(jdbc.queryForList("select t.name from question_tags qt join tags t using (tag_id) where qt.question_id = ? order by t.name",
                String.class, questionId)).containsExactly("code", "tech");

        // 3. Another user searches -> relevant question returned, unrelated filtered, tags restrict
        String relevantQuery = "/api/search?q=zephyrnet+router+dropping+hostel&tags=tech";
        JsonNode hits = call(HttpMethod.GET, relevantQuery, bob, null, 200);
        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).get("questionId").asLong()).isEqualTo(questionId);
        assertThat(hits.get(0).get("bodyPreview").asText()).contains("zephyrnet router every hour");
        assertThat(hits.get(0).get("isResolved").asBoolean()).isFalse();
        assertThat(call(HttpMethod.GET, "/api/search?q=dinner+cold+mess+tasteless&tags=tech", bob, null, 200)).isEmpty();
        assertThat(call(HttpMethod.GET, "/api/search?q=zephyrnet+router&tags=math", bob, null, 200)).isEmpty();
        assertThat(call(HttpMethod.GET, "/api/search?q=zephyrnet+router+dropping&mode=lexical&tags=tech", bob, null, 200)).hasSize(1);

        // 4. Open the question; add an answer, comments, a reply, a vote
        call(HttpMethod.GET, "/api/questions/" + questionId, bob, null, 200);
        long answerId = call(HttpMethod.POST, "/api/questions/" + questionId + "/answers", bob, "{\"body\":\"Update the router firmware\"}", 201)
                .get("answerId").asLong();
        long carolComment = call(HttpMethod.POST, "/api/questions/" + questionId + "/comments", carol, "{\"body\":\"Try changing the DNS\"}", 201)
                .get("commentId").asLong();
        long aliceReply = call(HttpMethod.POST, "/api/comments/" + carolComment + "/replies", alice, "{\"body\":\"Tried, no luck\"}", 201)
                .get("commentId").asLong();
        JsonNode voted = call(HttpMethod.PUT, "/api/comments/" + carolComment + "/vote", bob, "{\"voteType\":\"UP\"}", 200);
        assertThat(voted.get("upCount").asInt()).isEqualTo(1);

        JsonNode thread = call(HttpMethod.GET, "/api/questions/" + questionId + "/comments", bob, null, 200);
        assertThat(thread).hasSize(1);
        assertThat(thread.get(0).get("upCount").asInt()).isEqualTo(1);
        assertThat(thread.get(0).get("myVote").asText()).isEqualTo("UP");
        assertThat(thread.get(0).get("replies").get(0).get("commentId").asLong()).isEqualTo(aliceReply);

        // 5. The question's author marks "+", then selects the correct answer -> resolved
        JsonNode plus = call(HttpMethod.POST, "/api/questions/" + questionId + "/interest", alice, null, 200);
        assertThat(plus.get("interestCount").asInt()).isEqualTo(1);
        JsonNode resolved = call(HttpMethod.PUT, "/api/questions/" + questionId + "/accepted-answer", alice,
                "{\"answerId\":" + answerId + "}", 200);
        assertThat(resolved.get("isResolved").asBoolean()).isTrue();
        assertThat(resolved.get("acceptedAnswerId").asLong()).isEqualTo(answerId);
        call(HttpMethod.PUT, "/api/questions/" + questionId + "/accepted-answer", bob, "{\"answerId\":" + answerId + "}", 403);

        // 6. Notifications were generated for the right people, and can be opened
        assertThat(call(HttpMethod.GET, "/api/notifications/unread-count", alice, null, 200).get("unreadCount").asInt()).isEqualTo(1);
        JsonNode aliceNotes = call(HttpMethod.GET, "/api/notifications", alice, null, 200);
        assertThat(aliceNotes.get(0).get("type").asText()).isEqualTo("COMMENT_ON_QUESTION");
        assertThat(aliceNotes.get(0).get("questionId").asLong()).isEqualTo(questionId);
        assertThat(aliceNotes.get(0).get("commentId").asLong()).isEqualTo(carolComment);
        JsonNode carolNotes = call(HttpMethod.GET, "/api/notifications", carol, null, 200);
        assertThat(carolNotes.get(0).get("type").asText()).isEqualTo("REPLY_TO_COMMENT");
        assertThat(carolNotes.get(0).get("commentId").asLong()).isEqualTo(aliceReply);
        assertThat(call(HttpMethod.GET, "/api/notifications", bob, null, 200)).isEmpty();
        call(HttpMethod.PUT, "/api/notifications/" + aliceNotes.get(0).get("notificationId").asLong() + "/read", alice, null, 200);
        assertThat(call(HttpMethod.GET, "/api/notifications/unread-count", alice, null, 200).get("unreadCount").asInt()).isZero();

        // 7. The resolved question is part of the searchable knowledge base, shown as resolved with its "+" count
        JsonNode afterResolve = call(HttpMethod.GET, relevantQuery, bob, null, 200);
        assertThat(afterResolve).hasSize(1);
        assertThat(afterResolve.get(0).get("isResolved").asBoolean()).isTrue();
        assertThat(afterResolve.get(0).get("interestCount").asInt()).isEqualTo(1);

        // 8. Editing the text re-embeds it (so later searches follow the new text)
        call(HttpMethod.PUT, "/api/questions/" + questionId, alice,
                "{\"title\":\"Zephyrnet firmware crash after update\",\"body\":\"the router reboots randomly after firmware update\"}", 200);
        assertThat(jdbc.queryForObject("select embedding::text from question_embeddings where question_id = ?", String.class, questionId))
                .isNotEqualTo(vectorBefore);
        assertThat(call(HttpMethod.GET, "/api/search?q=zephyrnet+firmware+reboots&tags=tech", bob, null, 200)).hasSize(1);
        assertThat(call(HttpMethod.GET, "/api/search?q=hostel+hourly+laptop+disconnects&tags=tech", bob, null, 200)).isEmpty();

        // 9. Homepage discovery (union of tags, most "+" first) and the profile
        long second = call(HttpMethod.POST, "/api/questions", carol,
                "{\"title\":\"E2E second question\",\"body\":\"unrelated\",\"tags\":[\"code\"]}", 201).get("questionId").asLong();
        JsonNode home = call(HttpMethod.GET, "/api/questions?tags=tech&tags=code", bob, null, 200);
        assertThat(home.get("items")).extracting(n -> n.get("questionId").asLong()).containsExactly(questionId, second); // 1 "+" before 0
        assertThat(home.get("totalItems").asInt()).isEqualTo(2);
        JsonNode mine = call(HttpMethod.GET, "/api/me/questions", alice, null, 200);
        assertThat(mine.get("items")).extracting(n -> n.get("questionId").asLong()).containsExactly(questionId);

        // 10. Ownership still enforced on the committed data
        call(HttpMethod.PUT, "/api/questions/" + questionId, bob, "{\"title\":\"x\",\"body\":\"y\"}", 403);
        call(HttpMethod.PUT, "/api/answers/" + answerId, alice, "{\"body\":\"x\"}", 403);
        call(HttpMethod.PUT, "/api/comments/" + carolComment, alice, "{\"body\":\"x\"}", 403);

        // 11. Database sanity: every table this flow touched holds consistent rows
        assertThat(count("select interest_count from questions where question_id = ?", questionId))
                .isEqualTo(count("select count(*) from question_interests where question_id = ?", questionId));
        assertThat(count("select count(*) from questions where accepted_answer_id is not null and is_resolved = false "
                + "and question_id = ?", questionId)).isZero();
    }
}
