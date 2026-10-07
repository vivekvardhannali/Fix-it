package com.fixit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import com.fixit.dto.QuestionRequest;
import com.fixit.embedding.EmbeddingPurpose;
import com.fixit.embedding.EmbeddingService;
import com.fixit.entity.User;
import com.fixit.repository.UserRepository;
import com.fixit.search.HybridRanking;
import com.fixit.search.LexicalScorer;
import com.fixit.search.SearchResult;
import com.fixit.search.SemanticScorer;
import com.fixit.search.SemanticSearchService;
import com.fixit.service.QuestionService;

/**
 * LIVE test against the real Jina API. Skipped unless the environment variable JINA_LIVE_KEY is set, so the normal build
 * never makes network calls or spends quota:
 *
 *     JINA_LIVE_KEY=... mvn test -Dtest=JinaLiveTest
 *
 * It stores real 2048-number vectors in the real database (and removes them afterwards), runs real searches, and prints a
 * score table - use it to choose SEARCH_SEMANTIC_WEIGHT and the thresholds.
 */
@EnabledIfEnvironmentVariable(named = "JINA_LIVE_KEY", matches = ".+")
@SpringBootTest(properties = { "app.embedding.provider=jina", "app.embedding.model=jina-embeddings-v4",
        "app.embedding.api-url=https://api.jina.ai/v1/embeddings", "app.embedding.dimension=2048",
        "app.embedding.api-key=${JINA_LIVE_KEY}" })
class JinaLiveTest {

    @Autowired EmbeddingService embeddings;
    @Autowired SemanticScorer semantic;
    @Autowired LexicalScorer lexical;
    @Autowired SemanticSearchService semanticSearch;
    @Autowired com.fixit.search.SearchProperties searchProperties;
    @Autowired QuestionService questionService;
    @Autowired UserRepository users;
    @Autowired JdbcTemplate jdbc;

    private Long userId;
    private final Map<Long, String> titles = new LinkedHashMap<>();

    @AfterEach
    void cleanUp() {
        for (Long id : titles.keySet()) {
            jdbc.update("delete from question_embeddings where question_id = ?", id);
            jdbc.update("delete from question_tags where question_id = ?", id);
            jdbc.update("delete from questions where question_id = ?", id);
        }
        if (userId != null) jdbc.update("delete from users where user_id = ?", userId);
    }

    private static double cosine(float[] a, float[] b) {
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.length; i++) { dot += a[i] * b[i]; na += a[i] * a[i]; nb += b[i] * b[i]; }
        return dot / Math.sqrt(na * nb);
    }

    @Test
    void realApi_returnsFullSizeFiniteVectors_andQueryPassageModesBothWork() {
        float[] passage = embeddings.generateEmbedding("WiFi keeps disconnecting in hostel");
        float[] query = embeddings.generateEmbedding("wifi disconnecting hostel", EmbeddingPurpose.QUERY);
        float[] unrelated = embeddings.generateEmbedding("Mess food quality complaint, dinner served cold");
        assertThat(passage).hasSize(2048);
        assertThat(query).hasSize(2048);
        assertThat(cosine(query, passage)).isGreaterThan(cosine(query, unrelated));
        System.out.printf("%n[live] cosine(query, related passage)=%.3f   cosine(query, unrelated passage)=%.3f%n",
                cosine(query, passage), cosine(query, unrelated));
    }

    @Test
    void realApi_endToEnd_storeThenSearch_andPrintScoreTable() {
        org.junit.jupiter.api.Assumptions.assumeTrue(jdbc.queryForObject("select count(*) from questions", Integer.class) == 0,
                "needs an EMPTY database (other questions would change the rankings): run `node frontend/test/seed.mjs --clear` first");
        userId = users.save(new User("livejina@smail.iitm.ac.in")).getId();
        String[][] data = {
                { "WiFi keeps disconnecting in hostel", "my laptop loses the wifi connection every hour in hostel block", "tech" },
                { "Cannot connect to hostel WiFi", "laptop wifi disconnects hostel connection", "tech" },
                { "Internet very slow in the library", "download speeds drop a lot in the evening", "tech" },
                { "How to register for elective courses", "course registration electives deadline portal", "math" },
                { "Mess food quality complaint", "dinner is served cold and tasteless", "others" },
                { "Where to submit bonafide certificate application", "need a bonafide certificate for a bank loan", "others" } };
        for (String[] d : data) {
            long id = questionService.create(userId, new QuestionRequest(d[0], d[1], List.of(d[2]))).questionId();
            titles.put(id, d[0]);
        }
        // every question got a real 2048-dim vector stored by the after-commit hook
        for (Long id : titles.keySet()) {
            assertThat(jdbc.queryForObject("select vector_dims(embedding) from question_embeddings where question_id = ?",
                    Integer.class, id)).isEqualTo(2048);
        }

        String[] queries = { "wifi disconnecting in my hostel room", "internet speed is slow", "how do I pick my elective courses",
                "food in the mess is bad", "quantum chromodynamics lagrangian", "my laptop battery drains fast" };
        System.out.println("\n[live] score table: semantic cosine / lexical (normalised BM25) / hybrid at w=0.7");
        List<String> failures = new ArrayList<>();
        for (String q : queries) {
            Map<Long, Double> sem = semantic.score(q, List.of());
            Map<Long, Double> lex = lexical.score(q, List.of());
            var hybrid = HybridRanking.combine(sem, lex, 0.7);
            System.out.println("  query: \"" + q + "\"");
            titles.forEach((id, title) -> {
                double h = hybrid.stream().filter(s -> s.questionId().equals(id)).findFirst().map(s -> s.score()).orElse(0.0);
                System.out.printf("     sem %.3f | lex %.3f | hyb %.3f   %s%n", sem.getOrDefault(id, 0.0), lex.getOrDefault(id, 0.0), h, title);
            });
        }

        // sanity on the clearest cases (not exact numbers)
        Map<Long, Double> wifi = semantic.score("wifi disconnecting in my hostel room", List.of());
        long wifi1 = idOf("WiFi keeps disconnecting in hostel"), wifi2 = idOf("Cannot connect to hostel WiFi"), mess = idOf("Mess food quality complaint");
        assertThat(wifi.get(wifi1)).isGreaterThan(wifi.get(mess));
        assertThat(wifi.get(wifi2)).isGreaterThan(wifi.get(mess));
        Map<Long, Double> food = semantic.score("food in the mess is bad", List.of());
        assertThat(food.get(mess)).isEqualTo(food.values().stream().max(Double::compare).orElseThrow());

        // the shipped PROVISIONAL values (weight / hybrid threshold) separate clear matches from clear non-matches on this sample
        double w = searchProperties.semanticWeight(), threshold = searchProperties.hybridThreshold();
        String[][] clear = { { "wifi disconnecting in my hostel room", "WiFi keeps disconnecting in hostel", "Cannot connect to hostel WiFi" },
                { "internet speed is slow", "Internet very slow in the library" },
                { "how do I pick my elective courses", "How to register for elective courses" },
                { "food in the mess is bad", "Mess food quality complaint" } };
        for (String[] c : clear) {
            var hyb = HybridRanking.combine(semantic.score(c[0], List.of()), lexical.score(c[0], List.of()), w);
            for (var sq : hyb) {
                boolean shouldMatch = List.of(c).subList(1, c.length).contains(titles.get(sq.questionId()));
                if (shouldMatch && sq.score() < threshold) failures.add("MISSED  '" + c[0] + "' -> " + titles.get(sq.questionId()) + " " + sq.score());
                if (!shouldMatch && sq.score() >= threshold) System.out.printf("[live] would also show (threshold %.2f): '%s' -> %s (%.3f)%n", threshold, c[0], titles.get(sq.questionId()), sq.score());
            }
        }
        // a query matching nothing at all returns nothing
        for (var sq : HybridRanking.combine(semantic.score("quantum chromodynamics lagrangian", List.of()),
                lexical.score("quantum chromodynamics lagrangian", List.of()), w)) {
            if (sq.score() >= threshold) System.out.printf("[live] would also show for the NONSENSE query (threshold %.2f): %s (%.3f)%n", threshold, titles.get(sq.questionId()), sq.score());
        }
        assertThat(failures).as("provisional thresholds on the sample").isEmpty();

        // the real service path: semantic search honours tags and threshold
        List<SearchResult> results = semanticSearch.search("wifi disconnecting in my hostel room", List.of("tech"));
        assertThat(results).isNotEmpty();
        assertThat(results.get(0).questionId()).isIn(wifi1, wifi2);
        assertThat(results).allSatisfy(r -> assertThat(r.tags()).contains("tech"));
    }

    private long idOf(String title) {
        return titles.entrySet().stream().filter(e -> e.getValue().equals(title)).findFirst().orElseThrow().getKey();
    }
}
