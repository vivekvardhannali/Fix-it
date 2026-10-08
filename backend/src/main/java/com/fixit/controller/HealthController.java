package com.fixit.controller;

import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;

import com.fixit.embedding.EmbeddingProperties;
import com.fixit.embedding.EmbeddingStatus;
import com.fixit.search.SearchProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    private final JdbcTemplate jdbc;
    private final EmbeddingProperties embedding;
    private final SearchProperties search;

    public HealthController(JdbcTemplate jdbc, EmbeddingProperties embedding, SearchProperties search) {
        this.jdbc = jdbc;
        this.embedding = embedding;
        this.search = search;
    }

    @GetMapping("/api/ping")
    public Map<String, String> ping() {
        return Map.of("status", "ok", "app", "fix-it");
    }

    @GetMapping("/api/health/db")
    public Map<String, Object> db() {
        String database = jdbc.queryForObject("select current_database()", String.class);
        String pgvector = jdbc.queryForObject(
                "select extversion from pg_extension where extname = 'vector'", String.class);
        return Map.of("database", database, "pgvector", pgvector);
    }

    /** Which embedding settings are still placeholders. Never returns values or the API key. */
    @GetMapping("/api/health/embedding")
    public EmbeddingStatus embedding() {
        return new EmbeddingStatus(embedding.isConfigured(), embedding.missing(), embedding.textSource());
    }

    /** Which search tuning values are still placeholders. Never returns values. */
    @GetMapping("/api/health/search")
    public Map<String, Object> search() {
        return Map.of("missing", search.missing());
    }
}
