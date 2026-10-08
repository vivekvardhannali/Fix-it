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

/** TESTING-CONVENIENCE (D23) switched OFF - the shipped default: no tokens, one cookie login, Authorization headers ignored. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TabSessionDisabledTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired LoginThrottle throttle;
    @Autowired RateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        throttle.clear();
        rateLimiter.clear();
    }

    @Test
    void noTokenIsIssued_andABearerHeaderChangesNothing() throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/register").contentType("application/json")
                .content("{\"username\":\"dave_t\",\"smail\":\"dave_t@smail.iitm.ac.in\",\"password\":\"correct horse battery\"}"))
                .andExpect(status().isCreated()).andReturn();
        JsonNode body = mapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("sessionToken").isNull()).isTrue();
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);

        mvc.perform(get("/api/me").session(session).header("Authorization", "Bearer some-token"))   // ignored: the cookie decides
                .andExpect(status().isOk()).andExpect(jsonPath("$.username").value("dave_t"));
        mvc.perform(get("/api/me").header("Authorization", "Bearer some-token")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me").session(session)).andExpect(jsonPath("$.perTabSessions").value(false));
    }
}
