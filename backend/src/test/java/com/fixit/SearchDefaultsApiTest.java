package com.fixit;

import static org.assertj.core.api.Assertions.assertThat;
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

import com.fixit.search.SearchProperties;

/**
 * The SHIPPED search defaults (provisional values chosen from the live Jina experiment). The embedding API key is blank in
 * tests, so semantic/hybrid report "unavailable" while lexical search works with its default threshold.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SearchDefaultsApiTest {

    @Autowired SearchProperties properties;
    @Autowired MockMvc mvc;

    @Test
    void provisionalDefaultsAreInPlace() {
        assertThat(properties.semanticWeight()).isEqualTo(0.7);       // 70% semantic / 30% lexical
        assertThat(properties.hybridThreshold()).isEqualTo(0.38);
        assertThat(properties.semanticThreshold()).isEqualTo(0.6);
        assertThat(properties.lexicalThreshold()).isEqualTo(0.3);
        assertThat(properties.missing()).isEmpty();
    }

    @Test
    void lexicalSearchWorksOutOfTheBox_semanticNeedsTheEmbeddingKey() throws Exception {
        mvc.perform(get("/api/search").param("q", "wifi hostel").param("mode", "lexical").with(authentication(TestAuth.withId(777L, "someone"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isArray());      // works without an embedding key (the results depend on the data)
        for (String mode : new String[] { "hybrid", "semantic" }) {
            mvc.perform(get("/api/search").param("q", "wifi hostel").param("mode", mode).with(authentication(TestAuth.withId(777L, "someone"))))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(content().string(not(containsString("EMBEDDING_"))));
        }
    }
}
