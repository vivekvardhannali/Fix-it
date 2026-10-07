package com.fixit.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fixit.entity.Tag;
import com.fixit.repository.TagRepository;

/** Tag rows for the fixed {@link TagCatalog}. Users cannot create tags. */
@Service
public class TagService {

    private static final Logger log = LoggerFactory.getLogger(TagService.class);

    private final TagRepository tags;
    private final TagCatalog catalog;
    private final JdbcTemplate jdbc;

    public TagService(TagRepository tags, TagCatalog catalog, JdbcTemplate jdbc) {
        this.tags = tags;
        this.catalog = catalog;
        this.jdbc = jdbc;
    }

    /** Makes sure every catalog tag has a row (idempotent, safe if several servers start at once). */
    @EventListener(ApplicationReadyEvent.class)
    public void ensureCatalogRows() {
        for (String name : catalog.names()) {
            jdbc.update("insert into tags (name) values (?) on conflict do nothing", name);
        }
        log.info("Tag catalog ready: {}", catalog.names());
    }

    /** The available tags, in the catalog's order. */
    @Transactional
    public List<Tag> listAll() {
        ensureCatalogRows();
        List<Tag> result = new ArrayList<>(tags.findAllByOrderByNameAsc().stream()
                .filter(t -> catalog.canonicalOrNull(t.getName()) != null).toList());
        List<String> order = catalog.names();
        result.sort(Comparator.comparingInt(t -> order.indexOf(catalog.canonicalOrNull(t.getName()))));
        return result;
    }

    /**
     * Validates the names against the catalog (unknown/blank -> 400) and returns the tag rows, without duplicates.
     */
    @Transactional
    public List<Tag> resolve(List<String> rawNames) {
        List<String> names = catalog.requireKnown(rawNames);
        List<Tag> result = new ArrayList<>();
        for (String name : names) {
            result.add(findOrCreate(name));
        }
        return result;
    }

    /**
     * Internal: the row for a name, created if missing. Not for user input - use {@link #resolve}, which checks the catalog.
     */
    @Transactional
    public Tag findOrCreate(String rawName) {
        String name = normalize(rawName);
        return tags.findByNameIgnoreCase(name).orElseGet(() -> tags.save(new Tag(name)));
    }

    public static String normalize(String raw) {
        return raw.trim().replaceAll("\\s+", " ");
    }
}
