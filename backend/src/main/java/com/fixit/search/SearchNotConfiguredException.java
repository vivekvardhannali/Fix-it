package com.fixit.search;

/** A search tuning value (threshold / weight) is still a placeholder. Surfaces to clients as a generic 503. */
public class SearchNotConfiguredException extends RuntimeException {
    public SearchNotConfiguredException(String message) {
        super(message);
    }
}
