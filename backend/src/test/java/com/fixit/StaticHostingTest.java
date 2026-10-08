package com.fixit;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/** The backend serves the website (same origin as the API) without a login; everything with data stays protected. */
@SpringBootTest
@AutoConfigureMockMvc
class StaticHostingTest {

    @Autowired MockMvc mvc;

    @Test
    void thePagesLoadWithoutALogin() throws Exception {
        mvc.perform(get("/")).andExpect(status().isOk()).andExpect(forwardedUrl("index.html"));        // the home page
        mvc.perform(get("/index.html")).andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/html"))
                .andExpect(content().string(containsString("Fix It")));
        mvc.perform(get("/login.html")).andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/html"));
    }

    @Test
    void stylesheetsAndScriptsAreServed() throws Exception {
        mvc.perform(get("/css/fixit.css")).andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith("text/css"));
        mvc.perform(get("/js/api.js")).andExpect(status().isOk())
                .andExpect(header().string("Content-Type", startsWith("text/javascript")));
    }

    @Test
    void publicStaticRoutesAnswer404ForMissingFiles_whileEverythingElseAnswers401() throws Exception {
        mvc.perform(get("/js/nothing.js")).andExpect(status().isNotFound());       // allowed path, no such file
        mvc.perform(get("/css/nothing.css")).andExpect(status().isNotFound());
        mvc.perform(get("/img/nothing.png")).andExpect(status().isNotFound());
        mvc.perform(get("/nothing.html")).andExpect(status().isNotFound());
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());           // data stays protected
        mvc.perform(get("/api/questions")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/nothing")).andExpect(status().isUnauthorized());
        mvc.perform(get("/application.properties")).andExpect(status().isUnauthorized());   // not a public path
        mvc.perform(get("/secret/page.html")).andExpect(status().isUnauthorized());        // only top-level pages are public
    }

    @Test
    void onlyReadingIsPublic() throws Exception {
        mvc.perform(post("/login.html")).andExpect(status().isUnauthorized());
        mvc.perform(post("/css/fixit.css")).andExpect(status().isUnauthorized());
    }

    @Test
    void securityHeadersAreSetOnPagesAndOnTheApi() throws Exception {
        for (String path : new String[] { "/index.html", "/api/ping", "/api/me" }) {
            mvc.perform(get(path))
                    .andExpect(header().string("Content-Security-Policy", containsString("default-src 'self'")))
                    .andExpect(header().string("Content-Security-Policy", containsString("frame-ancestors 'none'")))
                    .andExpect(header().string("Content-Security-Policy", containsString("form-action 'self'")))
                    .andExpect(header().string("X-Content-Type-Options", "nosniff"));
        }
    }

    @Test
    void thePolicyBlocksInlineScriptsAndOutsideSources() throws Exception {
        String csp = mvc.perform(get("/index.html")).andReturn().getResponse().getHeader("Content-Security-Policy");
        org.assertj.core.api.Assertions.assertThat(csp)
                .doesNotContain("script-src")                // falls back to default-src 'self': no inline, no eval, no CDN
                .doesNotContain("unsafe-eval").doesNotContain("http:").doesNotContain("https:").doesNotContain("*");
        org.assertj.core.api.Assertions.assertThat(csp.replaceAll("style-src 'self' 'unsafe-inline'", "")).doesNotContain("unsafe-inline");
    }
}
