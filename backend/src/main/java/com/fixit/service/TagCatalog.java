package com.fixit.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.fixit.exception.BadRequestException;

/**
 * The fixed set of tags a question can have: tech, math, code, others (configurable as {@code app.tags.allowed}).
 * Users pick from this list when asking or editing a question and when filtering; nobody can invent a tag.
 * Matching ignores case; the canonical (stored/shown) name is the one written here.
 */
@Component
public class TagCatalog {

    private final Map<String, String> canonicalByLowerName = new LinkedHashMap<>();

    public TagCatalog(@Value("${app.tags.allowed:tech,math,code,others}") List<String> allowed) {
        for (String raw : allowed) {
            String name = raw == null ? "" : raw.trim().replaceAll("\\s+", " ");
            if (!name.isEmpty()) {
                canonicalByLowerName.putIfAbsent(name.toLowerCase(Locale.ROOT), name.toLowerCase(Locale.ROOT));
            }
        }
        if (canonicalByLowerName.isEmpty()) {
            throw new IllegalStateException("app.tags.allowed must list at least one tag");
        }
    }

    /** The allowed tag names, in the configured order. */
    public List<String> names() {
        return List.copyOf(canonicalByLowerName.values());
    }

    /** @return the canonical name, or {@code null} if the tag is not in the catalog */
    public String canonicalOrNull(String raw) {
        return raw == null ? null : canonicalByLowerName.get(raw.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT));
    }

    /**
     * Validates names against the catalog: none blank, none unknown. Returns canonical names without duplicates, in the
     * order given.
     *
     * @throws BadRequestException naming the allowed tags
     */
    public List<String> requireKnown(List<String> raw) {
        List<String> result = new ArrayList<>();
        for (String name : raw) {
            if (name == null || name.isBlank()) {
                throw new BadRequestException("Tag names must not be blank");
            }
            String canonical = canonicalOrNull(name);
            if (canonical == null) {
                throw new BadRequestException("Unknown tag '" + abbreviate(name.trim()) + "'. Choose from: "
                        + String.join(", ", names()));
            }
            if (!result.contains(canonical)) {
                result.add(canonical);
            }
        }
        return result;
    }

    private static String abbreviate(String s) {
        return s.length() <= 30 ? s : s.substring(0, 30) + "…";
    }
}
