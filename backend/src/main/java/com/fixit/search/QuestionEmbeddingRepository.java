package com.fixit.search;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Plain SQL on question_embeddings. Vectors travel as pgvector text literals ("[0.1,0.2]") cast in SQL,
 * so no special JDBC/Hibernate type registration is needed.
 */
@Repository
public class QuestionEmbeddingRepository {

    public record Stored(String model, String textHash, int dimension) {
    }

    private final JdbcTemplate jdbc;

    public QuestionEmbeddingRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Stored> find(long questionId) {
        return jdbc.query("select model, text_hash, vector_dims(embedding) from question_embeddings where question_id = ?",
                rs -> rs.next() ? Optional.of(new Stored(rs.getString(1), rs.getString(2).trim(), rs.getInt(3)))
                        : Optional.<Stored>empty(),
                questionId);
    }

    public void upsert(long questionId, float[] vector, String model, String textHash) {
        jdbc.update("insert into question_embeddings (question_id, embedding, model, text_hash) "
                + "values (?, CAST(? AS vector), ?, ?) "
                + "on conflict (question_id) do update set embedding = excluded.embedding, model = excluded.model, "
                + "text_hash = excluded.text_hash, updated_at = CURRENT_TIMESTAMP",
                questionId, literal(vector), model, textHash);
    }

    /**
     * Cosine similarity (1 - cosine distance) between the query vector and every stored vector that was produced by
     * {@code model} with {@code dimension}. {@code lowerCaseTagNames} restricts candidates to questions having ANY of
     * these tags (null/empty = no restriction). No threshold or limit is applied here.
     */
    public Map<Long, Double> cosineScores(float[] query, String model, int dimension, Collection<String> lowerCaseTagNames) {
        boolean filterByTags = lowerCaseTagNames != null && !lowerCaseTagNames.isEmpty();
        String sql = "select e.question_id, 1 - (e.embedding <=> CAST(? AS vector)) from question_embeddings e "
                + "where e.model = ? and vector_dims(e.embedding) = ? "
                + (filterByTags
                        ? "and exists (select 1 from question_tags qt join tags t on t.tag_id = qt.tag_id "
                                + "where qt.question_id = e.question_id and lower(t.name) = any(?)) "
                        : "");
        Map<Long, Double> scores = new LinkedHashMap<>();
        jdbc.query(con -> {
            var ps = con.prepareStatement(sql);
            ps.setString(1, literal(query));
            ps.setString(2, model);
            ps.setInt(3, dimension);
            if (filterByTags) {
                ps.setArray(4, con.createArrayOf("text", lowerCaseTagNames.toArray()));
            }
            return ps;
        }, rs -> {
            scores.put(rs.getLong(1), rs.getDouble(2));
        });
        return scores;
    }

    static String literal(float[] vector) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(Float.toString(vector[i]));
        }
        return sb.append(']').toString();
    }
}
