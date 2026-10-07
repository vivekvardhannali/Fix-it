package com.fixit.embedding;

import org.springframework.stereotype.Component;

import com.fixit.entity.Question;

/**
 * Builds the text sent to the embedding provider for a question.
 * PROPOSED DEFAULT: title + body (property app.embedding.text-source). Not yet confirmed by the project owner;
 * it changes search behaviour, so confirm before Phase 15.
 */
@Component
public class EmbeddingTextBuilder {

    private final EmbeddingTextSource source;

    public EmbeddingTextBuilder(EmbeddingProperties properties) {
        this.source = properties.textSource();
    }

    public String build(Question question) {
        return build(question.getTitle(), question.getBody());
    }

    public String build(String title, String body) {
        return switch (source) {
            case TITLE_AND_BODY -> title + "\n\n" + body;
            case TITLE_ONLY -> title;
            case BODY_ONLY -> body;
        };
    }
}
