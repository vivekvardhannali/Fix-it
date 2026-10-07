package com.fixit.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.fixit.embedding.EmbeddingProperties;
import com.fixit.search.SearchProperties;

/** Reminds the developer at startup which settings still use placeholders. */
@Component
class PlaceholderConfigWarning {

    private static final Logger log = LoggerFactory.getLogger(PlaceholderConfigWarning.class);

    private final String googleClientId;
    private final boolean googleEnabled;
    private final EmbeddingProperties embedding;
    private final SearchProperties search;

    PlaceholderConfigWarning(
            @Value("${spring.security.oauth2.client.registration.google.client-id}") String googleClientId,
            @Value("${app.auth.google-enabled:false}") boolean googleEnabled,
            EmbeddingProperties embedding, SearchProperties search) {
        this.googleEnabled = googleEnabled;
        this.search = search;
        this.googleClientId = googleClientId;
        this.embedding = embedding;
    }

    @EventListener(ApplicationReadyEvent.class)
    void warn() {
        if (googleEnabled && googleClientId.startsWith("PLACEHOLDER")) {
            log.warn("Google login uses PLACEHOLDER credentials - real sign-in will not work. "
                    + "See PLACEHOLDERS_TO_CHANGE.md");
        }
        if (!embedding.isConfigured()) {
            log.warn("Embedding provider not configured (missing {}) - semantic search is unavailable. "
                    + "See PLACEHOLDERS_TO_CHANGE.md", embedding.missing());
        }
        if (!search.missing().isEmpty()) {
            log.warn("Search tuning values not set (missing {}) - see PLACEHOLDERS_TO_CHANGE.md", search.missing());
        }
    }
}
