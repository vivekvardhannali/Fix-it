package com.fixit.search;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Optional one-off backfill at startup: set EMBEDDING_REINDEX_ON_STARTUP=true after configuring the provider. */
@Component
class EmbeddingReindexRunner {

    private final QuestionEmbeddingService service;
    private final boolean enabled;

    EmbeddingReindexRunner(QuestionEmbeddingService service,
            @Value("${app.embedding.reindex-on-startup:false}") boolean enabled) {
        this.service = service;
        this.enabled = enabled;
    }

    @EventListener(ApplicationReadyEvent.class)
    void run() {
        if (enabled) {
            service.reindexAll();
        }
    }
}
