package com.fixit.embedding;

/**
 * Stand-in used until a real provider exists. Always fails with "not configured" - it never fabricates
 * vectors, so semantic features cannot silently appear to work.
 * To add the real provider: create a class extending {@link AbstractEmbeddingService}, make it a Spring bean and
 * annotate it {@code @Primary}; injection points then receive it instead of this placeholder (see EmbeddingConfig).
 */
public class PlaceholderEmbeddingService extends AbstractEmbeddingService {

    public PlaceholderEmbeddingService(EmbeddingProperties properties) {
        super(properties);
    }

    @Override
    protected float[] callProvider(String text, EmbeddingPurpose purpose) {
        throw new EmbeddingNotConfiguredException(
                "No embedding provider implementation exists yet for provider '" + properties.provider() + "'");
    }
}
