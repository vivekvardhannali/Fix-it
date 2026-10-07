package com.fixit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.fixit.embedding.EmbeddingNotConfiguredException;
import com.fixit.embedding.EmbeddingService;
import com.fixit.embedding.PlaceholderEmbeddingService;

/** A provider other than "jina" that has no implementation falls back to the refuse-everything placeholder. */
@SpringBootTest(properties = "app.embedding.provider=PLACEHOLDER_TO_BE_DECIDED")
class PlaceholderProviderTest {

    @Autowired EmbeddingService embeddings;

    @Test
    void unknownProviderUsesThePlaceholder() {
        assertThat(embeddings).isInstanceOf(PlaceholderEmbeddingService.class);
        assertThatThrownBy(() -> embeddings.generateEmbedding("x")).isInstanceOf(EmbeddingNotConfiguredException.class);
    }
}
