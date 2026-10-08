package com.fixit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class DatabaseConnectionTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void connectsToFixItDatabase() {
        assertThat(jdbc.queryForObject("select current_database()", String.class)).isEqualTo("fix_it");
    }

    @Test
    void pgvectorIsInstalledAndUsable() {
        assertThat(jdbc.queryForObject(
                "select extname from pg_extension where extname = 'vector'", String.class)).isEqualTo("vector");
        Double distance = jdbc.queryForObject("select '[1,2,3]'::vector <=> '[1,2,4]'::vector", Double.class);
        assertThat(distance).isNotNull();
    }
}
