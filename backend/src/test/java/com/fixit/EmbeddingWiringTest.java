package com.fixit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fixit.embedding.*;

/** Phase 14 wiring: with the shipped placeholder config the app starts and fails cleanly, never fakes vectors. */
// the search tuning values are blanked here to exercise the "still unset" reporting (shipped defaults are provisional values)
@SpringBootTest(properties = { "app.search.semantic-threshold=", "app.search.lexical-threshold=",
        "app.search.hybrid-threshold=", "app.search.semantic-weight=" })
@AutoConfigureMockMvc
@Import(EmbeddingWiringTest.ProbeController.class)
class EmbeddingWiringTest {

    @TestConfiguration
    @RestController
    static class ProbeController {
        private final EmbeddingService embeddings;

        ProbeController(EmbeddingService embeddings) {
            this.embeddings = embeddings;
        }

        @GetMapping("/api/ping/embed-probe")
        float[] probe() {
            return embeddings.generateEmbedding("hello");
        }
    }

    @Autowired EmbeddingService embeddings;
    @Autowired EmbeddingProperties properties;
    @Autowired MockMvc mvc;

    @Test
    void jinaIsSelectedByDefaultButRefusesToEmbedWithoutAnApiKey() {
        assertThat(embeddings).isInstanceOf(JinaEmbeddingService.class);          // provider=jina is the default
        assertThat(properties.provider()).isEqualTo("jina");
        assertThat(properties.model()).isEqualTo("jina-embeddings-v4");
        assertThat(properties.apiUrl()).isEqualTo("https://api.jina.ai/v1/embeddings");
        assertThat(properties.dimension()).isEqualTo(2048);
        assertThat(properties.missing()).containsExactly("EMBEDDING_API_KEY");     // the only thing still missing
        assertThat(properties.isConfigured()).isFalse();
        assertThatThrownBy(() -> embeddings.generateEmbedding("hello")).isInstanceOf(EmbeddingNotConfiguredException.class);
    }

    @Test
    void statusEndpointListsWhatIsMissingWithoutLeakingValues() throws Exception {
        mvc.perform(get("/api/health/embedding").with(user("probe")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(false))
                .andExpect(jsonPath("$.missing.length()").value(1))
                .andExpect(jsonPath("$.missing[0]").value("EMBEDDING_API_KEY"))
                .andExpect(jsonPath("$.textSource").value("TITLE_AND_BODY"))
                .andExpect(content().string(not(containsString("PLACEHOLDER"))));
    }

    @Test
    void searchStatusEndpointListsUnsetTuningValues() throws Exception {
        mvc.perform(get("/api/health/search").with(user("probe")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.missing.length()").value(4))
                .andExpect(jsonPath("$.missing[0]").value("SEARCH_SEMANTIC_THRESHOLD"))
                .andExpect(jsonPath("$.missing[1]").value("SEARCH_LEXICAL_THRESHOLD"))
                .andExpect(jsonPath("$.missing[2]").value("SEARCH_HYBRID_THRESHOLD"))
                .andExpect(jsonPath("$.missing[3]").value("SEARCH_SEMANTIC_WEIGHT"));
    }

    @Test
    void embeddingFailureSurfacesAsGeneric503() throws Exception {
        mvc.perform(get("/api/ping/embed-probe").with(user("probe")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.message").value("Search is temporarily unavailable"))
                .andExpect(content().string(not(containsString("EMBEDDING_"))));
    }
}
