package com.fixit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import com.fixit.entity.User;
import com.fixit.repository.UserRepository;
import com.fixit.security.LoginThrottle;

import jakarta.persistence.EntityManager;

/** Username + password sign-up and login, with the SHIPPED config (Google sign-in off). */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PasswordAuthTest {

    static final String PASSWORD = "correct horse battery";

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired LoginThrottle throttle;
    @Autowired com.fixit.security.RateLimiter rateLimiter;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;

    @BeforeEach
    void setUp() {
        throttle.clear();   // the limiters are singletons shared by every test in this context
        rateLimiter.clear();
    }

    private static String register(String username, String smail, String password) {
        return "{\"username\":\"" + username + "\",\"smail\":\"" + smail + "\",\"password\":\"" + password + "\"}";
    }

    private static String login(String identifier, String password) {
        return "{\"identifier\":\"" + identifier + "\",\"password\":\"" + password + "\"}";
    }

    private MvcResult send(String url, String body) throws Exception {
        return mvc.perform(post(url).contentType("application/json").content(body)).andReturn();
    }

    private void registerAlice() throws Exception {
        assertThat(send("/api/auth/register", register("alice", "alice@smail.iitm.ac.in", PASSWORD)).getResponse().getStatus()).isEqualTo(201);
    }

    // ---- registration ----------------------------------------------------------------------

    @Test
    void registeringCreatesTheAccountStoresOnlyABcryptHash_andSignsTheUserIn() throws Exception {
        MvcResult result = send("/api/auth/register", register("Alice_01", "Alice@Smail.IITM.ac.in", PASSWORD));
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("\"username\":\"Alice_01\"").contains("\"smail\":\"alice@smail.iitm.ac.in\"");
        assertThat(body).doesNotContain(PASSWORD).doesNotContain("$2").doesNotContainIgnoringCase("password");

        em.flush();
        String hash = jdbc.queryForObject("select password_hash from users where username = 'Alice_01'", String.class);
        assertThat(hash).startsWith("$2").hasSize(60).doesNotContain(PASSWORD);        // BCrypt, salted

        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        assertThat(session).isNotNull();                                                // already logged in
        mvc.perform(get("/api/me").session(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("Alice_01"))
                .andExpect(jsonPath("$.smail").value("alice@smail.iitm.ac.in"))
                .andExpect(content().string(not(containsString("$2"))));
    }

    @Test
    void twoUsersWithTheSamePasswordGetDifferentHashes() throws Exception {
        send("/api/auth/register", register("alice", "alice@smail.iitm.ac.in", PASSWORD));
        send("/api/auth/register", register("bob", "bob@smail.iitm.ac.in", PASSWORD));
        em.flush();
        assertThat(jdbc.queryForList("select distinct password_hash from users", String.class)).hasSize(2);
    }

    @Test
    void invalidRegistrationsAreRejected_andCreateNothing() throws Exception {
        String[] bad = {
                register("ab", "a1@smail.iitm.ac.in", PASSWORD),                     // username too short
                register("has space", "a2@smail.iitm.ac.in", PASSWORD),
                register("-leading", "a3@smail.iitm.ac.in", PASSWORD),
                register("x".repeat(31), "a4@smail.iitm.ac.in", PASSWORD),
                register("nonIitm", "someone@gmail.com", PASSWORD),                  // not an IITM smail
                register("lookalike", "a5@smail.iitm.ac.in.evil.com", PASSWORD),
                register("shortpw", "a6@smail.iitm.ac.in", "short"),                 // < 8 characters
                register("commonpw", "a7@smail.iitm.ac.in", "password123"),          // too easy
                register("sameasname", "a8@smail.iitm.ac.in", "sameasname"),
                register("sameassmail", "cs23b043@smail.iitm.ac.in", "cs23b043"),
                register("toolong", "a9@smail.iitm.ac.in", "p".repeat(73)),          // BCrypt would silently ignore the tail
                "{}", "{\"username\":\"x\"}", "not json" };
        for (String body : bad) {
            assertThat(send("/api/auth/register", body).getResponse().getStatus()).as(body).isEqualTo(400);
        }
        em.flush();
        assertThat(jdbc.queryForObject("select count(*) from users", Integer.class)).isZero();
    }

    @Test
    void aPasswordMayContainAnyCharacters_includingUnicodeAndSpaces() throws Exception {
        assertThat(send("/api/auth/register", register("unicode", "u@smail.iitm.ac.in", "pässwörd ☃ with spaces & \\\"quotes\\\""))
                .getResponse().getStatus()).isEqualTo(201);
        assertThat(send("/api/auth/login", login("unicode", "pässwörd ☃ with spaces & \\\"quotes\\\"")).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void usernameAndSmailMustBeUnique_ignoringCase() throws Exception {
        registerAlice();
        assertThat(send("/api/auth/register", register("ALICE", "other@smail.iitm.ac.in", PASSWORD)).getResponse().getStatus()).isEqualTo(409);
        assertThat(send("/api/auth/register", register("alice2", "ALICE@smail.iitm.ac.in", PASSWORD)).getResponse().getStatus()).isEqualTo(409);
        em.flush();
        assertThat(jdbc.queryForObject("select count(*) from users", Integer.class)).isEqualTo(1);
    }

    @Test
    void aGoogleCreatedAccountCannotBeTakenOverByRegisteringItsSmail() throws Exception {
        users.save(new User("google.user@smail.iitm.ac.in"));                 // exists, has no password
        assertThat(send("/api/auth/register", register("hijacker", "google.user@smail.iitm.ac.in", PASSWORD))
                .getResponse().getStatus()).isEqualTo(409);
    }

    // ---- login -----------------------------------------------------------------------------

    @Test
    void loginWorksWithUsernameOrSmail_caseInsensitively_andStartsASession() throws Exception {
        registerAlice();
        for (String identifier : new String[] { "alice", "ALICE", "alice@smail.iitm.ac.in", "Alice@Smail.IITM.ac.in" }) {
            MvcResult r = send("/api/auth/login", login(identifier, PASSWORD));
            assertThat(r.getResponse().getStatus()).as(identifier).isEqualTo(200);
            MockHttpSession session = (MockHttpSession) r.getRequest().getSession(false);
            mvc.perform(get("/api/me").session(session)).andExpect(status().isOk()).andExpect(jsonPath("$.username").value("alice"));
        }
    }

    @Test
    void everyKindOfFailureLooksIdentical() throws Exception {
        registerAlice();
        users.save(new User("google.only@smail.iitm.ac.in"));                  // account without a password
        em.flush();
        String[] attempts = { login("alice", "wrong-password"), login("nobody", PASSWORD),
                login("google.only@smail.iitm.ac.in", PASSWORD), login("alice", "") };
        for (int i = 0; i < 3; i++) {
            MvcResult r = send("/api/auth/login", attempts[i]);
            assertThat(r.getResponse().getStatus()).isEqualTo(401);
            assertThat(r.getResponse().getContentAsString()).contains("Invalid username or password").doesNotContain("alice");
        }
        assertThat(send("/api/auth/login", attempts[3]).getResponse().getStatus()).isEqualTo(400);   // blank password = invalid request
    }

    @Test
    void tooManyFailuresLockTheAccount_evenForTheRightPassword_untilTheWindowPasses() throws Exception {
        registerAlice();
        for (int i = 0; i < 5; i++) {
            assertThat(send("/api/auth/login", login("alice", "wrong-" + i)).getResponse().getStatus()).isEqualTo(401);
        }
        mvc.perform(post("/api/auth/login").contentType("application/json").content(login("alice", PASSWORD)))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.message").value("Too many failed login attempts. Try again later."));
        // an unknown name is throttled exactly the same way (nothing is revealed about which accounts exist)
        for (int i = 0; i < 5; i++) send("/api/auth/login", login("ghost", "x" + i));
        assertThat(send("/api/auth/login", login("ghost", "anything")).getResponse().getStatus()).isEqualTo(429);
        // another account is unaffected (this address is still under its own limit)
        send("/api/auth/register", register("bob", "bob@smail.iitm.ac.in", PASSWORD));
        assertThat(send("/api/auth/login", login("bob", PASSWORD)).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void aSuccessfulLoginResetsTheFailureCount() throws Exception {
        registerAlice();
        for (int i = 0; i < 4; i++) send("/api/auth/login", login("alice", "wrong-" + i));
        assertThat(send("/api/auth/login", login("alice", PASSWORD)).getResponse().getStatus()).isEqualTo(200);
        for (int i = 0; i < 4; i++) send("/api/auth/login", login("alice", "wrong-again-" + i));
        assertThat(send("/api/auth/login", login("alice", PASSWORD)).getResponse().getStatus()).isEqualTo(200);   // not locked
    }

    // ---- sessions ---------------------------------------------------------------------------

    @Test
    void loggingInChangesTheSessionId_againstSessionFixation() throws Exception {
        registerAlice();
        MockHttpSession existing = new MockHttpSession();
        String before = existing.getId();
        mvc.perform(post("/api/auth/login").session(existing).contentType("application/json").content(login("alice", PASSWORD)))
                .andExpect(status().isOk());
        assertThat(existing.getId()).isNotEqualTo(before);
        mvc.perform(get("/api/me").session(existing)).andExpect(status().isOk());
    }

    @Test
    void logoutEndsTheSession() throws Exception {
        registerAlice();
        MockHttpSession session = (MockHttpSession) send("/api/auth/login", login("alice", PASSWORD)).getRequest().getSession(false);
        mvc.perform(post("/api/logout").session(session)).andExpect(status().isNoContent());
        mvc.perform(get("/api/me").session(session)).andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedRoutesStayProtected_andTheAuthRoutesArePublic() throws Exception {
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
        assertThat(send("/api/auth/login", "{}").getResponse().getStatus()).isEqualTo(400);       // reachable without a session
        assertThat(send("/api/auth/register", "{}").getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void googleSignInIsOffByDefault() throws Exception {
        mvc.perform(get("/oauth2/authorization/google")).andExpect(status().isUnauthorized());
    }

    @Test
    void meShowsNoUsernameForAnAccountThatHasNone() throws Exception {
        User google = users.save(new User("google.only@smail.iitm.ac.in"));
        mvc.perform(get("/api/me").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                        .authentication(TestAuth.as(google))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.username").doesNotExist())
                .andExpect(jsonPath("$.smail").value("google.only@smail.iitm.ac.in"));
    }

    @Test
    void theRequestObjectsNeverPrintThePassword() {
        assertThat(new com.fixit.dto.LoginRequest("alice", "s3cret-value").toString()).doesNotContain("s3cret-value");
        assertThat(new com.fixit.dto.RegisterRequest("alice", "a@smail.iitm.ac.in", "s3cret-value").toString()).doesNotContain("s3cret-value");
    }
}
