package com.fixit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fixit.entity.User;
import com.fixit.repository.UserRepository;

import jakarta.persistence.EntityManager;

/**
 * Checkpoint 21: authorization audit. Ownership is enforced by the backend, never only by the frontend.
 * Actors: A = question author, B = answerer / commenter, C = bystander.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthorizationAuditTest {

    /** The ONLY routes an anonymous caller may use. Anything new must be added here on purpose. */
    static final Set<String> PUBLIC_PATHS = Set.of("/api/ping", "/error", "/api/auth/login", "/api/auth/register");

    static final List<String> TABLES = List.of("users", "questions", "tags", "question_tags", "answers",
            "question_comments", "question_comment_votes", "question_interests", "notifications", "question_embeddings");

    @Autowired MockMvc mvc;
    @Autowired RequestMappingHandlerMapping handlerMapping;
    @Autowired UserRepository users;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;

    RequestPostProcessor a, b, c;
    long questionId, answerId, commentId, replyId, notificationId;

    @BeforeEach
    void setUp() throws Exception {
        a = authentication(TestAuth.as(users.save(new User("a@smail.iitm.ac.in"))));
        b = authentication(TestAuth.as(users.save(new User("b@smail.iitm.ac.in"))));
        c = authentication(TestAuth.as(users.save(new User("c@smail.iitm.ac.in"))));
        questionId = create("/api/questions", a, "{\"title\":\"t\",\"body\":\"b\",\"tags\":[\"tech\"]}", "questionId");
        answerId = create("/api/questions/" + questionId + "/answers", b, "{\"body\":\"answer\"}", "answerId");
        commentId = create("/api/questions/" + questionId + "/comments", b, "{\"body\":\"comment\"}", "commentId");
        replyId = create("/api/comments/" + commentId + "/replies", a, "{\"body\":\"reply\"}", "commentId");
        em.flush();
        notificationId = jdbc.queryForObject("select notification_id from notifications where comment_id = ?", Long.class, commentId);
    }

    private long create(String url, RequestPostProcessor as, String json, String idField) throws Exception {
        String res = mvc.perform(request(HttpMethod.POST, url).with(as).contentType("application/json").content(json))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return new ObjectMapper().readTree(res).get(idField).asLong();
    }

    private Map<String, String> snapshot() {
        em.flush();
        Map<String, String> hashes = new LinkedHashMap<>();
        for (String table : TABLES) {
            hashes.put(table, jdbc.queryForObject(
                    "select coalesce(md5(string_agg(x::text, '|' order by x::text)), '') from " + table + " x", String.class));
        }
        return hashes;
    }

    // ---- 1. every route is protected unless explicitly public ----------------------------------

    @Test
    void everyRegisteredRouteRejectsAnonymousCallers_exceptTheExplicitAllowlist() throws Exception {
        List<String> checked = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        for (RequestMappingInfo info : handlerMapping.getHandlerMethods().keySet()) {
            Set<String> methods = new TreeSet<>();
            info.getMethodsCondition().getMethods().forEach(m -> methods.add(m.name()));
            if (methods.isEmpty()) methods.add("GET");
            for (String pattern : info.getPathPatternsCondition().getPatternValues()) {
                String path = pattern.replaceAll("\\{[^/]+}", "1");
                for (String method : methods) {
                    int actual = mvc.perform(request(HttpMethod.valueOf(method), path).contentType("application/json").content("{}"))
                            .andReturn().getResponse().getStatus();
                    boolean isPublic = PUBLIC_PATHS.contains(path);
                    checked.add(method + " " + pattern);
                    if (isPublic ? actual == 401 || actual == 403 : actual != 401) {
                        problems.add(method + " " + pattern + " -> " + actual + (isPublic ? " (should be public)" : " (should be 401)"));
                    }
                }
            }
        }
        assertThat(checked.size()).as("routes discovered: %s", checked).isGreaterThan(30);   // guards the enumeration itself
        assertThat(problems).as("routes not protected as intended").isEmpty();
    }

    @Test
    void healthEndpointsRevealingConfigurationRequireLogin() throws Exception {
        for (String path : new String[] { "/api/health/db", "/api/health/embedding", "/api/health/search" }) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
            mvc.perform(get(path).with(c)).andExpect(status().isOk());
        }
        mvc.perform(get("/api/ping")).andExpect(status().isOk());
    }

    // ---- 2. ownership matrix ------------------------------------------------------------------

    record Case(String what, HttpMethod method, String path, String body, RequestPostProcessor owner,
            List<RequestPostProcessor> notOwners) {
    }

    @Test
    void onlyTheOwnerMayModify_everyoneElseGets403_andNothingChanges() throws Exception {
        List<Case> cases = List.of(
                new Case("edit question", HttpMethod.PUT, "/api/questions/" + questionId,
                        "{\"title\":\"new\",\"body\":\"new\"}", a, List.of(b, c)),
                new Case("change question tags", HttpMethod.PUT, "/api/questions/" + questionId + "/tags",
                        "{\"tags\":[\"math\"]}", a, List.of(b, c)),
                new Case("select accepted answer", HttpMethod.PUT, "/api/questions/" + questionId + "/accepted-answer",
                        "{\"answerId\":" + answerId + "}", a, List.of(b, c)),            // b wrote the answer: still no
                new Case("edit answer", HttpMethod.PUT, "/api/answers/" + answerId,
                        "{\"body\":\"edited\"}", b, List.of(a, c)),                       // a owns the question: still no
                new Case("edit comment", HttpMethod.PUT, "/api/comments/" + commentId,
                        "{\"body\":\"edited\"}", b, List.of(a, c)),
                new Case("edit reply", HttpMethod.PUT, "/api/comments/" + replyId,
                        "{\"body\":\"edited\"}", a, List.of(b, c)),
                new Case("mark notification read", HttpMethod.PUT, "/api/notifications/" + notificationId + "/read",
                        null, a, List.of(b, c)));

        List<String> problems = new ArrayList<>();
        for (Case cs : cases) {
            for (RequestPostProcessor intruder : cs.notOwners()) {
                Map<String, String> before = snapshot();
                var req = request(cs.method(), cs.path()).with(intruder);
                if (cs.body() != null) req.contentType("application/json").content(cs.body());
                int actual = mvc.perform(req).andReturn().getResponse().getStatus();
                if (actual != 403) problems.add(cs.what() + ": intruder got " + actual + " instead of 403");
                if (!before.equals(snapshot())) problems.add(cs.what() + ": a rejected request still changed the database");
            }
            Map<String, String> before = snapshot();
            var req = request(cs.method(), cs.path()).with(cs.owner());
            if (cs.body() != null) req.contentType("application/json").content(cs.body());
            int actual = mvc.perform(req).andReturn().getResponse().getStatus();
            if (actual != 200) problems.add(cs.what() + ": the owner got " + actual + " instead of 200");
            if (before.equals(snapshot())) problems.add(cs.what() + ": the owner's request changed nothing");
        }
        assertThat(problems).isEmpty();
    }

    @Test
    void anyLoggedInUserMayDoTheOpenActions() throws Exception {
        // C is a stranger to everything yet may answer, comment, reply, vote, mark "+", create tags, read, search-by-list
        mvc.perform(request(HttpMethod.POST, "/api/questions/" + questionId + "/answers").with(c)
                .contentType("application/json").content("{\"body\":\"mine\"}")).andExpect(status().isCreated());
        mvc.perform(request(HttpMethod.POST, "/api/questions/" + questionId + "/comments").with(c)
                .contentType("application/json").content("{\"body\":\"mine\"}")).andExpect(status().isCreated());
        mvc.perform(request(HttpMethod.POST, "/api/comments/" + commentId + "/replies").with(c)
                .contentType("application/json").content("{\"body\":\"mine\"}")).andExpect(status().isCreated());
        mvc.perform(request(HttpMethod.PUT, "/api/comments/" + commentId + "/vote").with(c)
                .contentType("application/json").content("{\"voteType\":\"UP\"}")).andExpect(status().isOk());
        mvc.perform(request(HttpMethod.POST, "/api/questions/" + questionId + "/interest").with(c)).andExpect(status().isOk());
        mvc.perform(request(HttpMethod.POST, "/api/tags").with(c)
                .contentType("application/json").content("{\"name\":\"Anything\"}")).andExpect(status().isMethodNotAllowed());   // free tag creation no longer exists
        mvc.perform(get("/api/questions/" + questionId).with(c)).andExpect(status().isOk());
        mvc.perform(get("/api/questions").with(c)).andExpect(status().isOk());
    }

    // ---- 3. a caller cannot choose who they are or force server-controlled fields ---------------

    @Test
    void forgedAuthorAndServerControlledFieldsInRequestBodiesAreIgnored() throws Exception {
        long bId = users.findBySmail("b@smail.iitm.ac.in").orElseThrow().getId();
        long aId = users.findBySmail("a@smail.iitm.ac.in").orElseThrow().getId();
        String forged = "{\"title\":\"t\",\"body\":\"b\",\"tags\":[\"tech\"],\"authorId\":" + bId + ",\"interestCount\":999,"
                + "\"isResolved\":true,\"resolved\":true,\"acceptedAnswerId\":" + answerId + ",\"createdAt\":\"2000-01-01T00:00:00\"}";
        long forgedQuestion = create("/api/questions", a, forged, "questionId");
        em.flush();
        Map<String, Object> row = jdbc.queryForMap("select author_id, interest_count, is_resolved, accepted_answer_id, "
                + "created_at > '2020-01-01' as recent from questions where question_id = ?", forgedQuestion);
        assertThat(((Number) row.get("author_id")).longValue()).isEqualTo(aId);            // the caller, not who the body claims
        assertThat(row.get("interest_count")).isEqualTo(0);
        assertThat(row.get("is_resolved")).isEqualTo(false);
        assertThat(row.get("accepted_answer_id")).isNull();
        assertThat(row.get("recent")).isEqualTo(true);

        // same on update: the author can't be reassigned, counters can't be set
        mvc.perform(request(HttpMethod.PUT, "/api/questions/" + questionId).with(a).contentType("application/json")
                .content("{\"title\":\"t2\",\"body\":\"b2\",\"authorId\":" + bId + ",\"interestCount\":50}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.authorId").value(aId)).andExpect(jsonPath("$.interestCount").value(0));

        // and for answers / comments
        long forgedAnswer = create("/api/questions/" + questionId + "/answers", c, "{\"body\":\"x\",\"authorId\":" + bId + "}", "answerId");
        long forgedComment = create("/api/questions/" + questionId + "/comments", c, "{\"body\":\"x\",\"authorId\":" + bId + "}", "commentId");
        em.flush();
        long cId = users.findBySmail("c@smail.iitm.ac.in").orElseThrow().getId();
        assertThat(jdbc.queryForObject("select author_id from answers where answer_id = ?", Long.class, forgedAnswer)).isEqualTo(cId);
        assertThat(jdbc.queryForObject("select author_id from question_comments where comment_id = ?", Long.class, forgedComment)).isEqualTo(cId);
    }

    @Test
    void anAcceptedAnswerMustBelongToTheQuestionEvenForItsOwner() throws Exception {
        long other = create("/api/questions", c, "{\"title\":\"other\",\"body\":\"b\",\"tags\":[\"tech\"]}", "questionId");
        long foreign = create("/api/questions/" + other + "/answers", b, "{\"body\":\"x\"}", "answerId");
        Map<String, String> before = snapshot();
        mvc.perform(request(HttpMethod.PUT, "/api/questions/" + questionId + "/accepted-answer").with(a)
                .contentType("application/json").content("{\"answerId\":" + foreign + "}")).andExpect(status().isBadRequest());
        assertThat(snapshot()).isEqualTo(before);
    }
}
