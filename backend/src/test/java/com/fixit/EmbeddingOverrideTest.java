package com.fixit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import com.fixit.embedding.EmbeddingProperties;
import com.fixit.embedding.EmbeddingService;
import com.fixit.embedding.PlaceholderEmbeddingService;

/** Proves the documented way to plug in the real provider: register an EmbeddingService bean marked @Primary. */
@SpringBootTest(properties = {
        "app.embedding.provider=stub", "app.embedding.model=stub-model", "app.embedding.api-url=http://stub",
        "app.embedding.api-key=k", "app.embedding.dimension=3" })
@Import(EmbeddingOverrideTest.RealProviderStandIn.class)
class EmbeddingOverrideTest {

    @TestConfiguration
    static class RealProviderStandIn {
        @Bean
        @Primary
        EmbeddingService realProvider() {
            return text -> new float[] { 1f, 2f, 3f };
        }
    }

    @Autowired EmbeddingService embeddings;
    @Autowired EmbeddingProperties properties;

    @Test
    void customProviderReplacesThePlaceholder() {
        assertThat(embeddings).isNotInstanceOf(PlaceholderEmbeddingService.class);
        assertThat(properties.isConfigured()).isTrue();
        assertThat(embeddings.generateEmbedding("x")).hasSize(3);
    }
}
