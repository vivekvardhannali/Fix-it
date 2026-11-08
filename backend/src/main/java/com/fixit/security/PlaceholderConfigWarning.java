package com.fixit.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.fixit.embedding.EmbeddingProperties;
import com.fixit.search.SearchProperties;

/** Reminds the developer at startup which settings still use placeholders. */
@Component
class PlaceholderConfigWarning {

    private static final Logger log = LoggerFactory.getLogger(PlaceholderConfigWarning.class);

    private final EmbeddingProperties embedding;
    private final SearchProperties search;

    PlaceholderConfigWarning(EmbeddingProperties embedding, SearchProperties search) {
        this.embedding = embedding;
        this.search = search;
    }

    @EventListener(ApplicationReadyEvent.class)
    void warn() {
        if (!embedding.isConfigured()) {
            log.warn("Embedding provider not configured (missing {}) - semantic search is unavailable. "
                    + "See PLACEHOLDERS_TO_CHANGE.md", embedding.missing());
        }
        if (!search.missing().isEmpty()) {
            log.warn("Search tuning values not set (missing {}) - see PLACEHOLDERS_TO_CHANGE.md", search.missing());
        }
    }
}
