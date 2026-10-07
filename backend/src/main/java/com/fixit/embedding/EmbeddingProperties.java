package com.fixit.embedding;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Embedding provider settings. All provider values are PLACEHOLDERS until the real provider is chosen
 * (see PLACEHOLDERS_TO_CHANGE.md). Nothing here assumes a particular provider, model, URL or dimension.
 */
@ConfigurationProperties(prefix = "app.embedding")
public record EmbeddingProperties(
        String provider,
        String model,
        String apiUrl,
        String apiKey,
        Integer dimension,
        EmbeddingTextSource textSource) {

    private static final String PLACEHOLDER_PREFIX = "PLACEHOLDER";

    public EmbeddingProperties {
        if (textSource == null) {
            textSource = EmbeddingTextSource.TITLE_AND_BODY;
        }
    }

    /** Names of the environment variables that still hold a placeholder / no value. */
    public List<String> missing() {
        List<String> missing = new ArrayList<>();
        if (isPlaceholder(provider)) missing.add("EMBEDDING_PROVIDER");
        if (isPlaceholder(model)) missing.add("EMBEDDING_MODEL");
        if (isPlaceholder(apiUrl)) missing.add("EMBEDDING_API_URL");
        if (isPlaceholder(apiKey)) missing.add("EMBEDDING_API_KEY");
        if (dimension == null || dimension <= 0) missing.add("EMBEDDING_DIMENSION");
        return missing;
    }

    public boolean isConfigured() {
        return missing().isEmpty();
    }

    private static boolean isPlaceholder(String value) {
        return value == null || value.isBlank() || value.startsWith(PLACEHOLDER_PREFIX);
    }

    /** Never prints the API key. */
    @Override
    public String toString() {
        return "EmbeddingProperties[provider=" + provider + ", model=" + model + ", apiUrl=" + apiUrl
                + ", apiKey=" + (isPlaceholder(apiKey) ? "<not set>" : "<hidden>") + ", dimension=" + dimension
                + ", textSource=" + textSource + "]";
    }
}
