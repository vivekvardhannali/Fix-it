package com.fixit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.fixit.embedding.EmbeddingProperties;
import com.fixit.embedding.EmbeddingService;
import com.fixit.embedding.JinaEmbeddingService;

/** With provider=jina and every setting present, the app is "configured" and uses the Jina implementation. */
@SpringBootTest(properties = { "app.embedding.provider=jina", "app.embedding.api-key=test-key-not-real",
        "app.embedding.model=jina-embeddings-v4", "app.embedding.api-url=https://api.jina.ai/v1/embeddings",
        "app.embedding.dimension=2048" })
class JinaWiringTest {

    @Autowired EmbeddingService embeddings;
    @Autowired EmbeddingProperties properties;

    @Test
    void jinaIsTheActiveProviderAndConfigured() {
        assertThat(embeddings).isInstanceOf(JinaEmbeddingService.class);
        assertThat(properties.isConfigured()).isTrue();
        assertThat(properties.toString()).doesNotContain("test-key-not-real");
    }
}
