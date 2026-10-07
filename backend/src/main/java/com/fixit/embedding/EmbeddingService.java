package com.fixit.embedding;

/** Turns text into an embedding vector. The provider behind it is replaceable. */
public interface EmbeddingService {

    /**
     * Embeds stored content ({@link EmbeddingPurpose#DOCUMENT}).
     *
     * @throws EmbeddingException if the provider is not configured, unavailable, or returns an invalid vector
     */
    float[] generateEmbedding(String text);

    /** Embeds text for the stated purpose. Providers that treat both the same need not override this. */
    default float[] generateEmbedding(String text, EmbeddingPurpose purpose) {
        return generateEmbedding(text);
    }
}
