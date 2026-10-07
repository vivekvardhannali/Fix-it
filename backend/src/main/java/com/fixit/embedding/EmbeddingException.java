package com.fixit.embedding;

/** Any failure to produce a valid embedding. Surfaces to API clients as a generic 503. */
public class EmbeddingException extends RuntimeException {
    public EmbeddingException(String message) {
        super(message);
    }

    public EmbeddingException(String message, Throwable cause) {
        super(message, cause);
    }
}
