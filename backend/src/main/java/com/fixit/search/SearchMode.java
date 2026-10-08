package com.fixit.search;

import java.util.Locale;

import com.fixit.exception.BadRequestException;

/** Which ranking a search request uses. HYBRID is the intended final search; the others help while tuning. */
public enum SearchMode {
    HYBRID, SEMANTIC, LEXICAL;

    public static SearchMode parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return HYBRID;
        }
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Unknown search mode '" + raw + "' (use hybrid, semantic or lexical)");
        }
    }
}
