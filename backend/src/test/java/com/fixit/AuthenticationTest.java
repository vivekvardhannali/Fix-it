package com.fixit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.fixit.entity.User;
import com.fixit.exception.AccountNotAllowedException;
import com.fixit.repository.UserRepository;
import com.fixit.security.FixItOidcUserService;
import com.fixit.service.UserProvisioningService;

/**
 * Checkpoint 5. Google itself is not contacted (placeholder credentials): the Google identity is simulated
 * and everything on Fix It's side of the callback is exercised for real.
 */
// Google sign-in is OFF by default now; these tests cover the (kept) Google path, so they turn it on
@SpringBootTest(properties = "app.auth.google-enabled=true")
@AutoConfigureMockMvc
@Transactional
class AuthenticationTest {

    @Autowired MockMvc mvc;
    @Autowired UserProvisioningService provisioning;
    @Autowired FixItOidcUserService oidcUserService;
    @Autowired UserRepository users;

    // ---- identity rules -------------------------------------------------

    @Test
    void newIitmUserIsCreated() {
        User u = provisioning.loginOrRegister("Alice@smail.iitm.ac.in", true);
        assertThat(u.getId()).isNotNull();
        assertThat(u.getSmail()).isEqualTo("alice@smail.iitm.ac.in"); // normalised
        assertThat(users.findBySmail("alice@smail.iitm.ac.in")).isPresent();
    }

    @Test
    void existingUserIsRetrievedNotDuplicated() {
        User first = provisioning.loginOrRegister("alice@smail.iitm.ac.in", true);
        User second = provisioning.loginOrRegister("ALICE@smail.iitm.ac.in", true);
        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(users.count()).isEqualTo(1);
    }

    @Test
    void nonIitmAndSuspiciousIdentitiesAreRejected() {
        for (String email : new String[] { "bob@gmail.com", "bob@iitm.ac.in", "bob@smail.iitm.ac.in.evil.com",
                "bob@evil.com@gmail.com", "@smail.iitm.ac.in", "", "  " }) {
            assertThatThrownBy(() -> provisioning.loginOrRegister(email, true))
                    .as(email).isInstanceOf(AccountNotAllowedException.class);
        }
        assertThatThrownBy(() -> provisioning.loginOrRegister(null, true)).isInstanceOf(AccountNotAllowedException.class);
        assertThatThrownBy(() -> provisioning.loginOrRegister("alice@smail.iitm.ac.in", false))
                .isInstanceOf(AccountNotAllowedException.class);
        assertThatThrownBy(() -> provisioning.loginOrRegister("alice@smail.iitm.ac.in", null))
                .isInstanceOf(AccountNotAllowedException.class);
        assertThat(users.count()).isZero();
    }

    // ---- the post-Google step (Google user -> Fix It principal) ---------

    private static DefaultOidcUser googleUser(String email, boolean verified) {
        OidcIdToken token = new OidcIdToken("t", Instant.now(), Instant.now().plusSeconds(60),
                Map.of("sub", "123", "email", email, "email_verified", verified));
        return new DefaultOidcUser(java.util.List.of(), token);
    }

    @Test
    void googleLoginProducesPrincipalWithFixItUserId() {
        var principal = toPrincipal(googleUser("alice@smail.iitm.ac.in", true));
        assertThat(principal.getUserId()).isEqualTo(users.findBySmail("alice@smail.iitm.ac.in").orElseThrow().getId());
    }

    @Test
    void googleLoginWithNonIitmAccountFailsAuthentication() {
        assertThatThrownBy(() -> toPrincipal(googleUser("bob@gmail.com", true)))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .extracting(e -> ((OAuth2AuthenticationException) e).getError().getErrorCode())
                .isEqualTo(FixItOidcUserService.ERROR_CODE);
        assertThat(users.findBySmail("bob@gmail.com")).isEmpty();
    }

    private com.fixit.security.FixItPrincipal toPrincipal(DefaultOidcUser g) {
        return oidcUserService.toPrincipal(g);
    }

    // ---- HTTP behaviour -------------------------------------------------

    @Test
    void protectedEndpointRejectsUnauthenticated() throws Exception {
        mvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
        mvc.perform(get("/api/questions/1")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/questions").contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedRequestWorks() throws Exception {
        User alice = users.save(new User("alice@smail.iitm.ac.in"));
        mvc.perform(get("/api/me").with(authentication(TestAuth.as(alice))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.smail").value("alice@smail.iitm.ac.in"))
                .andExpect(jsonPath("$.userId").value(alice.getId()));
    }

    @Test
    void publicEndpointsStayOpen() throws Exception {
        mvc.perform(get("/api/ping")).andExpect(status().isOk());
    }

    @Test
    void loginStartsGoogleRedirect() throws Exception {
        mvc.perform(get("/oauth2/authorization/google"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("https://accounts.google.com/")));
    }

    @Test
    void failedLoginRedirectsToFrontendWithErrorCode() throws Exception {
        var response = new MockHttpServletResponse();
        com.fixit.security.SecurityConfig.loginFailureHandler("http://localhost:3000").onAuthenticationFailure(
                new org.springframework.mock.web.MockHttpServletRequest(), response,
                new OAuth2AuthenticationException(new org.springframework.security.oauth2.core.OAuth2Error(
                        FixItOidcUserService.ERROR_CODE)));
        assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:3000/login?error=account_not_allowed");
    }

    @Test
    void logoutOnlyViaPost() throws Exception {
        mvc.perform(post("/api/logout")).andExpect(status().isNoContent());
        mvc.perform(get("/api/logout")).andExpect(status().isUnauthorized());
    }
}
