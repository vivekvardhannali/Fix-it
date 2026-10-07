package com.fixit.embedding;

/**
 * Shared behaviour for every provider: refuses to run when unconfigured, wraps provider failures,
 * and validates the returned vector (present, expected dimension, finite numbers).
 * A real provider only implements {@link #callProvider}.
 */
public abstract class AbstractEmbeddingService implements EmbeddingService {

    protected final EmbeddingProperties properties;

    protected AbstractEmbeddingService(EmbeddingProperties properties) {
        this.properties = properties;
    }

    @Override
    public final float[] generateEmbedding(String text) {
        return generateEmbedding(text, EmbeddingPurpose.DOCUMENT);
    }

    @Override
    public final float[] generateEmbedding(String text, EmbeddingPurpose purpose) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Text to embed must not be blank");
        }
        if (!properties.isConfigured()) {
            throw new EmbeddingNotConfiguredException(
                    "Embedding provider is not configured; missing " + properties.missing());
        }
        float[] vector;
        try {
            vector = callProvider(text, purpose);
        } catch (EmbeddingException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new EmbeddingException("Embedding provider call failed", e);
        }
        validate(vector);
        return vector;
    }

    /** Calls the real embedding API. Only reached when all settings are present. */
    protected abstract float[] callProvider(String text, EmbeddingPurpose purpose);

    private void validate(float[] vector) {
        if (vector == null || vector.length == 0) {
            throw new EmbeddingException("Embedding provider returned no vector");
        }
        if (vector.length != properties.dimension()) {
            throw new EmbeddingException("Embedding has dimension " + vector.length
                    + " but EMBEDDING_DIMENSION is " + properties.dimension());
        }
        for (float v : vector) {
            if (Float.isNaN(v) || Float.isInfinite(v)) {
                throw new EmbeddingException("Embedding contains a non-finite value");
            }
        }
    }
}
