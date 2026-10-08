package com.fixit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fixit.security.LoginThrottle;
import com.fixit.security.RateLimiter;
import com.fixit.security.TabSessionService;

/**
 * TESTING-CONVENIENCE (per-tab sessions, D23), switched ON: every browser tab can be a different account because a Bearer token
 * identifies the caller and overrides the shared session cookie.
 */
@SpringBootTest(properties = "app.auth.per-tab-sessions=true")
@AutoConfigureMockMvc
@Transactional
class TabSessionTest {

    static final String PASSWORD = "correct horse battery";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired TabSessionService tabSessions;
    @Autowired LoginThrottle throttle;
    @Autowired RateLimiter rateLimiter;

    record Person(String username, String token, MockHttpSession session, long userId) {
    }

    @BeforeEach
    void setUp() {
        tabSessions.clear();
        throttle.clear();
        rateLimiter.clear();
    }

    private Person signUp(String username) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/register").contentType("application/json")
                .content("{\"username\":\"" + username + "\",\"smail\":\"" + username + "@smail.iitm.ac.in\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isCreated()).andReturn();
        JsonNode body = mapper.readTree(result.getResponse().getContentAsString());
        return new Person(username, body.get("sessionToken").asText(), (MockHttpSession) result.getRequest().getSession(false), body.get("userId").asLong());
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    @Test
    void loginAndSignUpHandOutAToken_andStillStartTheCookieSession() throws Exception {
        Person alice = signUp("alice_t");
        assertThat(alice.token()).hasSizeGreaterThanOrEqualTo(40).matches("[A-Za-z0-9_-]+");   // 256 random bits, URL-safe
        assertThat(alice.session()).isNotNull();                                                  // cookie login still works as before
        MvcResult login = mvc.perform(post("/api/auth/login").contentType("application/json")
                .content("{\"identifier\":\"alice_t\",\"password\":\"" + PASSWORD + "\"}")).andExpect(status().isOk()).andReturn();
        String second = mapper.readTree(login.getResponse().getContentAsString()).get("sessionToken").asText();
        assertThat(second).isNotEqualTo(alice.token());                                          // each login = its own tab's token
        mvc.perform(get("/api/me").session(alice.session())).andExpect(status().isOk());          // cookie alone works
    }

    @Test
    void meReportsWhetherPerTabSessionsAreOn() throws Exception {
        Person alice = signUp("alice_t");
        mvc.perform(get("/api/me").session(alice.session())).andExpect(jsonPath("$.perTabSessions").value(true));
    }

    @Test
    void aTokenAloneIdentifiesTheCaller() throws Exception {
        Person alice = signUp("alice_t");
        mvc.perform(get("/api/me").header("Authorization", bearer(alice.token())))                  // no cookie at all
                .andExpect(status().isOk()).andExpect(jsonPath("$.username").value("alice_t"));
    }

    @Test
    void twoAccountsAtOnce_theTokenWinsOverTheSharedCookie() throws Exception {
        Person alice = signUp("alice_t"), bob = signUp("bob_t");
        // the browser's single cookie belongs to whoever logged in last (here: bob), yet each tab acts as itself
        mvc.perform(get("/api/me").session(bob.session()).header("Authorization", bearer(alice.token())))
                .andExpect(jsonPath("$.username").value("alice_t"));
        mvc.perform(get("/api/me").session(bob.session()).header("Authorization", bearer(bob.token())))
                .andExpect(jsonPath("$.username").value("bob_t"));
        mvc.perform(get("/api/me").session(alice.session()).header("Authorization", bearer(bob.token())))
                .andExpect(jsonPath("$.username").value("bob_t"));

        // writes are attributed to the token's owner too, not to the cookie's owner
        String body = mvc.perform(post("/api/questions").session(bob.session()).header("Authorization", bearer(alice.token()))
                .contentType("application/json").content("{\"title\":\"t\",\"body\":\"b\",\"tags\":[\"tech\"]}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        assertThat(mapper.readTree(body).get("authorId").asLong()).isEqualTo(alice.userId());
        assertThat(mapper.readTree(body).get("authorName").asText()).isEqualTo("alice_t");
    }

    @Test
    void anUnknownOrRevokedTokenMeansNotLoggedIn_neverTheCookiesOwner() throws Exception {
        Person alice = signUp("alice_t");
        mvc.perform(get("/api/me").session(alice.session()).header("Authorization", bearer("not-a-real-token")))
                .andExpect(status().isUnauthorized());                                              // a valid cookie does not rescue it
        mvc.perform(get("/api/questions").header("Authorization", bearer("not-a-real-token"))).andExpect(status().isUnauthorized());
    }

    @Test
    void loggingOutRevokesTheTabsToken() throws Exception {
        Person alice = signUp("alice_t"), bob = signUp("bob_t");
        mvc.perform(post("/api/logout").header("Authorization", bearer(alice.token()))).andExpect(status().isNoContent());
        mvc.perform(get("/api/me").header("Authorization", bearer(alice.token()))).andExpect(status().isUnauthorized());   // alice's tab is out
        mvc.perform(get("/api/me").header("Authorization", bearer(bob.token()))).andExpect(status().isOk());               // bob's tab is not
    }

    @Test
    void otherAuthorizationHeadersAreIgnored_andTheCookieStillWorks() throws Exception {
        Person alice = signUp("alice_t");
        for (String header : new String[] { "Basic YWxpY2U6cHc=", "Bearer", "Bearer   ", "bearer", "Token abc" }) {
            mvc.perform(get("/api/me").session(alice.session()).header("Authorization", header))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.username").value("alice_t"));
        }
        mvc.perform(get("/api/me").header("Authorization", "Basic YWxpY2U6cHc=")).andExpect(status().isUnauthorized());
    }

    @Test
    void theTokenOpensNoDoorsAnonymousUsersDoNotHave() throws Exception {
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/health/db").header("Authorization", bearer("anything"))).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/ping").header("Authorization", bearer("anything"))).andExpect(status().isOk());              // public stays public
    }

    @Test
    void theLoginResponseNeverContainsAPasswordOrHash() throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/register").contentType("application/json")
                .content("{\"username\":\"carol_t\",\"smail\":\"carol_t@smail.iitm.ac.in\",\"password\":\"" + PASSWORD + "\"}")).andReturn();
        assertThat(r.getResponse().getContentAsString()).doesNotContain(PASSWORD).doesNotContain("$2").doesNotContainIgnoringCase("password");
    }
}
