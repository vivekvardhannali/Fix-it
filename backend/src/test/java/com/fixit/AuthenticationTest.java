package com.fixit;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.fixit.entity.User;
import com.fixit.repository.UserRepository;

/** Which endpoints need a login, and how a logged-in request and logout behave. Sign-up/login itself: PasswordAuthTest. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthenticationTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;

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
        User alice = users.save(TestUsers.user("alice@smail.iitm.ac.in"));
        mvc.perform(get("/api/me").with(authentication(TestAuth.as(alice))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.smail").value("alice@smail.iitm.ac.in"))
                .andExpect(jsonPath("$.username").value(alice.getUsername()))
                .andExpect(jsonPath("$.userId").value(alice.getId()));
    }

    @Test
    void publicEndpointsStayOpen() throws Exception {
        mvc.perform(get("/api/ping")).andExpect(status().isOk());
    }

    @Test
    void logoutOnlyViaPost() throws Exception {
        mvc.perform(post("/api/logout")).andExpect(status().isNoContent());
        mvc.perform(get("/api/logout")).andExpect(status().isUnauthorized());
    }
}
