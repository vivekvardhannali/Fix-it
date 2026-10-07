package com.fixit.embedding;

import java.util.List;

/** Safe to expose: says what is still unset, never any value (and never the API key). */
public record EmbeddingStatus(boolean configured, List<String> missing, EmbeddingTextSource textSource) {
}
