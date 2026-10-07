package com.fixit.embedding;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import com.fasterxml.jackson.databind.ObjectMapper;

@Configuration
public class EmbeddingConfig {

    /**
     * Always registered: the safe fallback that refuses to embed. A real provider bean marked {@code @Primary} takes
     * precedence over it (deliberately not @ConditionalOnMissingBean: that depends on bean registration order).
     */
    @Bean
    EmbeddingService placeholderEmbeddingService(EmbeddingProperties properties) {
        return new PlaceholderEmbeddingService(properties);
    }

    /**
     * Selected by configuration (EMBEDDING_PROVIDER=jina). Even when selected, it still refuses to run until every
     * setting - including the API key - is filled in (see AbstractEmbeddingService).
     */
    @Bean
    @Primary
    @ConditionalOnProperty(name = "app.embedding.provider", havingValue = "jina")
    EmbeddingService jinaEmbeddingService(EmbeddingProperties properties, ObjectMapper mapper) {
        return new JinaEmbeddingService(properties, mapper);
    }
}
