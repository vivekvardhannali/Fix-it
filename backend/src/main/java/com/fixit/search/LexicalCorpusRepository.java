package com.fixit.search;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** SQL used by lexical search: the text corpus, a cheap change fingerprint, and the tag filter. */
@Repository
public class LexicalCorpusRepository {

    public record Doc(long questionId, String title, String body) {
    }

    private final JdbcTemplate jdbc;

    public LexicalCorpusRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Changes whenever a question is added or edited (edits always bump updated_at). */
    public String fingerprint() {
        return jdbc.queryForObject("select count(*) || ':' || coalesce(max(question_id), 0) || ':' "
                + "|| coalesce(max(updated_at)::text, '') from questions", String.class);
    }

    public void forEachQuestion(Consumer<Doc> consumer) {
        jdbc.query("select question_id, title, body from questions",
                rs -> {
                    consumer.accept(new Doc(rs.getLong(1), rs.getString(2), rs.getString(3)));
                });
    }

    /** Ids of questions having ANY of the given (lower-case) tag names. */
    public Set<Long> idsWithAnyTag(Collection<String> lowerCaseTagNames) {
        Set<Long> ids = new HashSet<>();
        jdbc.query(con -> {
            var ps = con.prepareStatement("select distinct qt.question_id from question_tags qt "
                    + "join tags t on t.tag_id = qt.tag_id where lower(t.name) = any(?)");
            ps.setArray(1, con.createArrayOf("text", lowerCaseTagNames.toArray()));
            return ps;
        }, rs -> {
            ids.add(rs.getLong(1));
        });
        return ids;
    }
}
