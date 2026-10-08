package com.fixit.search;

import java.util.List;
import java.util.Locale;

import com.fixit.exception.BadRequestException;
import com.fixit.service.TagService;

/** Validation/normalisation of a search request, shared by every search type. */
public final class SearchInput {

    private SearchInput() {
    }

    static final int MAX_QUERY_LENGTH = 500;
    static final int MAX_TAGS = 10;
    static final int MAX_TAG_LENGTH = 50;

    static String query(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BadRequestException("Search query must not be empty");
        }
        if (raw.trim().length() > MAX_QUERY_LENGTH) {
            throw new BadRequestException("Search query must be at most " + MAX_QUERY_LENGTH + " characters");
        }
        return raw.trim();
    }

    /** null/empty = no tag restriction (search everything). Otherwise lower-cased, normalised, de-duplicated. */
    public static List<String> tags(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<String> tags = raw.stream().map(t -> {
            if (t == null || t.isBlank()) {
                throw new BadRequestException("Tag names must not be blank");
            }
            if (t.trim().length() > MAX_TAG_LENGTH) {
                throw new BadRequestException("Tag names must be at most " + MAX_TAG_LENGTH + " characters");
            }
            return TagService.normalize(t).toLowerCase(Locale.ROOT);
        }).distinct().toList();
        if (tags.size() > MAX_TAGS) {
            throw new BadRequestException("At most " + MAX_TAGS + " tags can be selected");
        }
        return tags;
    }
}
