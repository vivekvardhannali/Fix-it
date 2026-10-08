package com.fixit;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/** Search tuning values blanked (as if nobody had chosen them): every search mode refuses instead of guessing. */
@SpringBootTest(properties = { "app.search.semantic-threshold=", "app.search.lexical-threshold=",
        "app.search.hybrid-threshold=", "app.search.semantic-weight=" })
@AutoConfigureMockMvc
class SearchNotConfiguredApiTest {

    @Autowired MockMvc mvc;

    @Test
    void everyModeIs503WhenTheTuningValuesAreUnset() throws Exception {
        for (String mode : new String[] { "hybrid", "semantic", "lexical" }) {
            mvc.perform(get("/api/search").param("q", "wifi").param("mode", mode).with(authentication(TestAuth.withId(777L, "someone"))))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.status").value(503))
                    .andExpect(jsonPath("$.message").value("Search is temporarily unavailable"))
                    .andExpect(content().string(not(containsString("SEARCH_"))));
        }
    }
}
