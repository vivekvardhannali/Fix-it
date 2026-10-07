package com.fixit.search;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Search tuning values. The three thresholds and {@code semanticWeight} are PLACEHOLDERS (null) until chosen by
 * experimentation with the real embedding model - see PLACEHOLDERS_TO_CHANGE.md. Nothing guesses a value:
 * a search that needs an unset value fails with {@link SearchNotConfiguredException}.
 */
@ConfigurationProperties(prefix = "app.search")
public record SearchProperties(
        Double semanticThreshold,   // cosine similarity, -1..1
        Double lexicalThreshold,    // normalised BM25, 0..1
        Double hybridThreshold,     // hybrid score, 0..1
        Double semanticWeight,      // 0..1; lexical weight = 1 - semanticWeight
        Integer maxResults,
        Double bm25K1,
        Double bm25B) {

    public static final int DEFAULT_MAX_RESULTS = 20;
    public static final double DEFAULT_K1 = 1.2;
    public static final double DEFAULT_B = 0.75;

    public SearchProperties {
        if (maxResults == null || maxResults <= 0) {
            maxResults = DEFAULT_MAX_RESULTS;
        }
        if (bm25K1 == null || bm25K1 < 0) {
            bm25K1 = DEFAULT_K1;
        }
        if (bm25B == null || bm25B < 0 || bm25B > 1) {
            bm25B = DEFAULT_B;
        }
    }

    /** Names of the environment variables that are still unset. */
    public List<String> missing() {
        List<String> missing = new ArrayList<>();
        if (semanticThreshold == null) missing.add("SEARCH_SEMANTIC_THRESHOLD");
        if (lexicalThreshold == null) missing.add("SEARCH_LEXICAL_THRESHOLD");
        if (hybridThreshold == null) missing.add("SEARCH_HYBRID_THRESHOLD");
        if (semanticWeight == null) missing.add("SEARCH_SEMANTIC_WEIGHT");
        return missing;
    }

    public double requireSemanticThreshold() {
        return require(semanticThreshold, -1, 1, "SEARCH_SEMANTIC_THRESHOLD (cosine similarity)");
    }

    public double requireLexicalThreshold() {
        return require(lexicalThreshold, 0, 1, "SEARCH_LEXICAL_THRESHOLD (normalised BM25)");
    }

    public double requireHybridThreshold() {
        return require(hybridThreshold, 0, 1, "SEARCH_HYBRID_THRESHOLD (hybrid score)");
    }

    public double requireSemanticWeight() {
        return require(semanticWeight, 0, 1, "SEARCH_SEMANTIC_WEIGHT (fraction)");
    }

    private static double require(Double value, double min, double max, String name) {
        if (value == null || value.isNaN() || value < min || value > max) {
            throw new SearchNotConfiguredException(name + " must be set to a number between " + min + " and " + max);
        }
        return value;
    }

    // copies, mainly for tests / experiments
    public SearchProperties withSemanticThreshold(Double v) { return new SearchProperties(v, lexicalThreshold, hybridThreshold, semanticWeight, maxResults, bm25K1, bm25B); }
    public SearchProperties withLexicalThreshold(Double v) { return new SearchProperties(semanticThreshold, v, hybridThreshold, semanticWeight, maxResults, bm25K1, bm25B); }
    public SearchProperties withHybridThreshold(Double v) { return new SearchProperties(semanticThreshold, lexicalThreshold, v, semanticWeight, maxResults, bm25K1, bm25B); }
    public SearchProperties withSemanticWeight(Double v) { return new SearchProperties(semanticThreshold, lexicalThreshold, hybridThreshold, v, maxResults, bm25K1, bm25B); }
    public SearchProperties withMaxResults(Integer v) { return new SearchProperties(semanticThreshold, lexicalThreshold, hybridThreshold, semanticWeight, v, bm25K1, bm25B); }
}
