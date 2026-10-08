package com.fixit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fixit.entity.User;
import com.fixit.repository.UserRepository;
import com.fixit.support.FakeEmbeddings;

import jakarta.persistence.EntityManager;

/**
 * Checkpoint 22: for each major API - successful request, invalid request, unauthorized request, missing resource -
 * plus a check that EVERY error uses the one JSON format and leaks no internals.
 */
@SpringBootTest(properties = { FakeEmbeddings.P_PROVIDER, FakeEmbeddings.P_MODEL, FakeEmbeddings.P_URL,
        FakeEmbeddings.P_KEY, FakeEmbeddings.P_DIM, FakeEmbeddings.P_THRESHOLD, FakeEmbeddings.P_LEXICAL_THRESHOLD,
        FakeEmbeddings.P_HYBRID_THRESHOLD, FakeEmbeddings.P_WEIGHT })
@Import({ FakeEmbeddings.Config.class, ErrorHandlingTest.Probe.class })
@AutoConfigureMockMvc
@Transactional
class ErrorHandlingTest {

    /** Endpoints that fail in ways real endpoints should never need to, to test the safety nets. */
    @TestConfiguration
    @RestController
    static class Probe {
        @GetMapping("/api/test/boom")
        String boom() {
            throw new IllegalStateException("secret internal detail: jdbc:postgresql://db-host/fix_it password=hunter2");
        }

        @GetMapping("/api/test/conflict")
        String conflict() {
            throw new DataIntegrityViolationException("duplicate key value violates unique constraint \"internal_idx\"");
        }

        @GetMapping("/api/test/denied")
        String denied() {
            throw new AccessDeniedException("internal rule XYZ-42");
        }
    }

    static final List<String> LEAK_MARKERS = List.of("Exception", "org.springframework", "com.fixit", "java.", "jdbc",
            "SQL", "hibernate", "postgres", "password", "hunter2", "internal_idx", "XYZ-42", "\tat ", "stackTrace", "trace");

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;
    @Autowired ObjectMapper mapper;

    RequestPostProcessor owner, stranger;
    long q, a, c, n;
    final List<String> problems = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        owner = authentication(TestAuth.as(users.save(new User("owner@smail.iitm.ac.in"))));
        stranger = authentication(TestAuth.as(users.save(new User("stranger@smail.iitm.ac.in"))));
        q = create("/api/questions", owner, "{\"title\":\"wifi hostel problem\",\"body\":\"laptop drops wifi\",\"tags\":[\"tech\"]}", "questionId");
        a = create("/api/questions/" + q + "/answers", stranger, "{\"body\":\"restart\"}", "answerId");
        c = create("/api/questions/" + q + "/comments", stranger, "{\"body\":\"try dns\"}", "commentId");
        em.flush();
        n = jdbc.queryForObject("select notification_id from notifications limit 1", Long.class);
    }

    private long create(String url, RequestPostProcessor as, String json, String idField) throws Exception {
        String res = mvc.perform(request(HttpMethod.POST, url).with(as).contentType("application/json").content(json))
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(res).get(idField).asLong();
    }

    /** Sends the request, checks the status, and for any error checks the body format and that nothing leaks. */
    private void expect(String label, int expectedStatus, HttpMethod method, String url, RequestPostProcessor as, String body) throws Exception {
        MockHttpServletRequestBuilder req = request(method, url);
        if (as != null) req.with(as);
        if (body != null) req.contentType("application/json").content(body);
        MockHttpServletResponse res = mvc.perform(req).andReturn().getResponse();
        String text = res.getContentAsString();
        if (res.getStatus() != expectedStatus) {
            problems.add(label + ": expected " + expectedStatus + " but got " + res.getStatus() + " " + text);
            return;
        }
        if (expectedStatus >= 400) {
            try {
                JsonNode json = mapper.readTree(text);
                if (json.path("status").asInt() != expectedStatus || json.path("error").asText().isBlank()
                        || json.path("message").asText().isBlank() || json.path("timestamp").asText().isBlank()) {
                    problems.add(label + ": error body is not in the standard format: " + text);
                }
            } catch (Exception e) {
                problems.add(label + ": error body is not JSON: " + text);
            }
            for (String marker : LEAK_MARKERS) {
                if (text.contains(marker)) problems.add(label + ": error body leaks '" + marker + "': " + text);
            }
        }
    }

    private void api(String name, HttpMethod method, String path, String okBody, int okStatus, String invalidPath,
            String invalidBody, String missingPath, RequestPostProcessor who) throws Exception {
        String okPath = path;
        expect(name + " / success", okStatus, method, okPath, who, okBody);
        expect(name + " / unauthenticated", 401, method, okPath, null, okBody);
        if (invalidPath != null || invalidBody != null) {
            expect(name + " / invalid", 400, method, invalidPath != null ? invalidPath : okPath, who,
                    invalidBody != null ? invalidBody : okBody);
        }
        if (missingPath != null) {
            expect(name + " / missing", 404, method, missingPath, who, okBody);
        }
    }

    @Test
    void everyMajorApiHandlesSuccessInvalidUnauthorizedAndMissing() throws Exception {
        String qp = "/api/questions/" + q;
        String json = "{\"title\":\"t\",\"body\":\"b\",\"tags\":[\"tech\"]}";

        api("create question", HttpMethod.POST, "/api/questions", json, 201, null, "{}", null, owner);
        api("get question", HttpMethod.GET, qp, null, 200, "/api/questions/abc", null, "/api/questions/999999", owner);
        api("update question", HttpMethod.PUT, qp, json, 200, null, "{\"title\":\"\",\"body\":\"b\"}", "/api/questions/999999", owner);
        api("list questions", HttpMethod.GET, "/api/questions", null, 200, "/api/questions?page=-1", null, null, owner);
        api("question tags (get)", HttpMethod.GET, qp + "/tags", null, 200, "/api/questions/abc/tags", null, "/api/questions/999999/tags", owner);
        api("question tags (replace)", HttpMethod.PUT, qp + "/tags", "{\"tags\":[\"math\"]}", 200, null, "{}", "/api/questions/999999/tags", owner);
        api("accepted answer", HttpMethod.PUT, qp + "/accepted-answer", "{\"answerId\":" + a + "}", 200, null, "{}", "/api/questions/999999/accepted-answer", owner);
        api("add answer", HttpMethod.POST, qp + "/answers", "{\"body\":\"x\"}", 201, null, "{\"body\":\"\"}", "/api/questions/999999/answers", stranger);
        api("list answers", HttpMethod.GET, qp + "/answers", null, 200, "/api/questions/abc/answers", null, "/api/questions/999999/answers", stranger);
        api("edit answer", HttpMethod.PUT, "/api/answers/" + a, "{\"body\":\"x\"}", 200, null, "{\"body\":\" \"}", "/api/answers/999999", stranger);
        api("add comment", HttpMethod.POST, qp + "/comments", "{\"body\":\"x\"}", 201, null, "{\"body\":\"\"}", "/api/questions/999999/comments", stranger);
        api("list comments", HttpMethod.GET, qp + "/comments", null, 200, "/api/questions/abc/comments", null, "/api/questions/999999/comments", stranger);
        api("reply", HttpMethod.POST, "/api/comments/" + c + "/replies", "{\"body\":\"x\"}", 201, null, "{}", "/api/comments/999999/replies", owner);
        api("edit comment", HttpMethod.PUT, "/api/comments/" + c, "{\"body\":\"x\"}", 200, null, "{\"body\":\"\"}", "/api/comments/999999", stranger);
        api("vote", HttpMethod.PUT, "/api/comments/" + c + "/vote", "{\"voteType\":\"UP\"}", 200, null, "{\"voteType\":\"SIDEWAYS\"}", "/api/comments/999999/vote", owner);
        api("remove vote", HttpMethod.DELETE, "/api/comments/" + c + "/vote", null, 200, "/api/comments/abc/vote", null, "/api/comments/999999/vote", owner);
        api("toggle +", HttpMethod.POST, qp + "/interest", null, 200, "/api/questions/abc/interest", null, "/api/questions/999999/interest", stranger);
        api("+ status", HttpMethod.GET, qp + "/interest", null, 200, "/api/questions/abc/interest", null, "/api/questions/999999/interest", stranger);
        api("notifications", HttpMethod.GET, "/api/notifications", null, 200, "/api/notifications?limit=abc", null, null, owner);
        api("unread count", HttpMethod.GET, "/api/notifications/unread-count", null, 200, null, null, null, owner);
        api("mark read", HttpMethod.PUT, "/api/notifications/" + n + "/read", null, 200, "/api/notifications/abc/read", null, "/api/notifications/999999/read", owner);
        api("list tags", HttpMethod.GET, "/api/tags", null, 200, null, null, null, owner);
        expect("tag creation no longer exists", 405, HttpMethod.POST, "/api/tags", owner, "{\"name\":\"Z\"}");
        api("search", HttpMethod.GET, "/api/search?q=wifi+hostel", null, 200, "/api/search", null, null, owner);
        api("me", HttpMethod.GET, "/api/me", null, 200, null, null, null, owner);
        api("my questions", HttpMethod.GET, "/api/me/questions", null, 200, "/api/me/questions?size=500", null, null, owner);

        assertThat(problems).isEmpty();
    }

    @Test
    void protocolLevelMistakesGetTheSameFormat() throws Exception {
        expect("unknown path", 404, HttpMethod.GET, "/api/does-not-exist", owner, null);
        expect("wrong HTTP method", 405, HttpMethod.DELETE, "/api/questions/" + q, owner, null);
        expect("malformed JSON", 400, HttpMethod.POST, "/api/questions", owner, "{not json");
        expect("JSON of the wrong shape", 400, HttpMethod.POST, "/api/questions", owner, "[1,2,3]");
        expect("empty body", 400, HttpMethod.POST, "/api/questions", owner, null);
        MockHttpServletResponse res = mvc.perform(request(HttpMethod.POST, "/api/questions").with(owner)
                .contentType("text/plain").content("hello")).andReturn().getResponse();
        assertThat(res.getStatus()).isEqualTo(415);
        assertThat(mapper.readTree(res.getContentAsString()).path("message").asText()).isEqualTo("Unsupported content type");
        assertThat(problems).isEmpty();
    }

    @Test
    void unexpectedFailuresNeverLeakInternals() throws Exception {
        expect("unexpected exception", 500, HttpMethod.GET, "/api/test/boom", owner, null);
        expect("database conflict", 409, HttpMethod.GET, "/api/test/conflict", owner, null);
        expect("access denied inside a controller", 403, HttpMethod.GET, "/api/test/denied", owner, null);
        String body = mvc.perform(request(HttpMethod.GET, "/api/test/boom").with(owner)).andReturn().getResponse().getContentAsString();
        assertThat(mapper.readTree(body).path("message").asText()).isEqualTo("Something went wrong");
        assertThat(problems).isEmpty();
    }

    @Test
    void authenticationAndAuthorizationFailuresUseTheSameFormat() throws Exception {
        expect("not logged in", 401, HttpMethod.GET, "/api/me", null, null);
        expect("not the owner", 403, HttpMethod.PUT, "/api/questions/" + q, stranger, "{\"title\":\"t\",\"body\":\"b\"}");
        assertThat(problems).isEmpty();
    }
}
