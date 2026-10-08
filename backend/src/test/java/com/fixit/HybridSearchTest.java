package com.fixit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.fixit.embedding.EmbeddingException;
import com.fixit.entity.Question;
import com.fixit.entity.QuestionTag;
import com.fixit.entity.User;
import com.fixit.repository.QuestionRepository;
import com.fixit.repository.QuestionTagRepository;
import com.fixit.repository.UserRepository;
import com.fixit.search.*;
import com.fixit.service.TagService;
import com.fixit.support.FakeEmbeddings;

import jakarta.persistence.EntityManager;

/**
 * Checkpoint 18 - hybrid MECHANICS (semantic part uses the test-only fake provider). The weights/thresholds used here
 * are test values; the real ones are placeholders to be tuned with the real model.
 */
@SpringBootTest(properties = { FakeEmbeddings.P_PROVIDER, FakeEmbeddings.P_MODEL, FakeEmbeddings.P_URL,
        FakeEmbeddings.P_KEY, FakeEmbeddings.P_DIM, FakeEmbeddings.P_THRESHOLD, FakeEmbeddings.P_LEXICAL_THRESHOLD,
        FakeEmbeddings.P_HYBRID_THRESHOLD, FakeEmbeddings.P_WEIGHT })
@Import(FakeEmbeddings.Config.class)
@Transactional
class HybridSearchTest {

    static final String QUERY = "wifi disconnecting hostel laptop";

    @Autowired SemanticScorer semanticScorer;
    @Autowired LexicalScorer lexicalScorer;
    @Autowired SearchResultAssembler assembler;
    @Autowired SearchProperties properties;
    @Autowired HybridSearchService hybrid;                 // uses the configured test weight 0.5
    @Autowired QuestionEmbeddingService embeddingService;
    @Autowired FakeEmbeddings fake;
    @Autowired UserRepository users;
    @Autowired QuestionRepository questions;
    @Autowired QuestionTagRepository questionTags;
    @Autowired TagService tagService;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;

    Question q1, q2, q3, q4;

    @BeforeEach
    void setUp() {
        fake.reset();
        User author = users.save(new User("alice@smail.iitm.ac.in"));
        q1 = make(author, "WiFi keeps disconnecting in hostel", "my laptop loses wifi connection every hour in hostel block", "Networking");
        q2 = make(author, "Cannot connect to hostel WiFi", "laptop wifi disconnects hostel connection", "Networking", "Hostel");
        q3 = make(author, "How to register for electives", "course registration electives deadline portal", "Academics");
        q4 = make(author, "Mess food quality complaint", "dinner served cold and tasteless", "Mess");
        em.flush();
        fake.reset();                                        // don't count the embeddings made during setup
    }

    private Question make(User author, String title, String body, String... tagNames) {
        Question q = questions.save(new Question(author, title, body));
        for (String t : tagNames) {
            questionTags.save(new QuestionTag(q, tagService.findOrCreate(t)));
        }
        em.flush();
        embeddingService.refresh(q.getId(), title, body);
        return q;
    }

    private HybridSearchService service(Double weight, Double threshold) {
        return new HybridSearchService(semanticScorer, lexicalScorer, assembler,
                properties.withSemanticWeight(weight).withHybridThreshold(threshold));
    }

    private List<Long> ids(List<SearchResult> results) {
        return results.stream().map(SearchResult::questionId).toList();
    }

    @Test
    void mixedWeight_everyScoreIsTheWeightedSumOfItsComponents_inDescendingOrder() {
        var results = service(0.6, 0.0).search(QUERY, null);
        assertThat(ids(results)).startsWith(q1.getId(), q2.getId());
        assertThat(results).allSatisfy(r -> {
            assertThat(r.semanticScore()).isNotNull();
            assertThat(r.lexicalScore()).isNotNull();
            assertThat(r.relevance()).isCloseTo(0.6 * r.semanticScore() + 0.4 * r.lexicalScore(), within(1e-9));
        });
        assertThat(results).isSortedAccordingTo((a, b) -> Double.compare(b.relevance(), a.relevance()));
    }

    @Test
    void weightOne_isSemanticOnly() {
        var hybridResults = service(1.0, 0.3).search(QUERY, null);
        var semanticResults = new SemanticSearchService(semanticScorer, assembler, properties.withSemanticThreshold(0.3)).search(QUERY, null);
        assertThat(ids(hybridResults)).isEqualTo(ids(semanticResults)).isNotEmpty();
        for (int i = 0; i < hybridResults.size(); i++) {
            assertThat(hybridResults.get(i).relevance()).isCloseTo(semanticResults.get(i).relevance(), within(1e-9));
            assertThat(hybridResults.get(i).lexicalScore()).isNull();       // lexical not even computed
        }
    }

    @Test
    void weightZero_isLexicalOnly_andNeedsNoEmbeddingProvider() {
        fake.failing = true;                                                // provider is down...
        var hybridResults = service(0.0, 0.2).search(QUERY, null);          // ...and weight 0 does not care
        var lexicalResults = new LexicalSearchService(lexicalScorer, assembler, properties.withLexicalThreshold(0.2)).search(QUERY, null);
        assertThat(ids(hybridResults)).isEqualTo(ids(lexicalResults)).isNotEmpty();
        assertThat(hybridResults).allSatisfy(r -> assertThat(r.semanticScore()).isNull());
    }

    @Test
    void weightZeroNeverCallsTheProvider() {
        service(0.0, 0.2).search(QUERY, null);
        assertThat(fake.calls.get()).isZero();
    }

    @Test
    void thresholdFiltersOnTheHybridScore() {
        var all = service(0.5, 0.0).search(QUERY, null);
        double cutoff = all.get(0).relevance();                             // only the best passes
        var top = service(0.5, cutoff).search(QUERY, null);
        assertThat(ids(top)).containsExactly(all.get(0).questionId());
        assertThat(service(0.5, 1.0).search(QUERY, null)).isEmpty();
        assertThat(service(0.5, 0.25).search("quantum chromodynamics lagrangian", null)).isEmpty();
        assertThat(all).allSatisfy(r -> assertThat(r.relevance()).isGreaterThanOrEqualTo(0.0));
    }

    @Test
    void unrelatedQuestionsAreFiltered_relevantOnesKept_withTheConfiguredTestValues() {
        var results = hybrid.search(QUERY, null);                           // weight 0.5, threshold 0.25
        assertThat(ids(results)).containsExactly(q1.getId(), q2.getId());
    }

    @Test
    void everyQuestionIsSearchable_aQuestionWithoutAnEmbeddingIsStillFoundLexically() {
        jdbc.update("delete from question_embeddings where question_id = ?", q1.getId());
        var results = service(0.5, 0.0).search(QUERY, null);
        SearchResult q1Result = results.stream().filter(r -> r.questionId().equals(q1.getId())).findFirst().orElseThrow();
        assertThat(q1Result.semanticScore()).isEqualTo(0.0);
        assertThat(q1Result.lexicalScore()).isGreaterThan(0.0);
        assertThat(service(1.0, 0.1).search(QUERY, null)).extracting(SearchResult::questionId).doesNotContain(q1.getId());
    }

    @Test
    void tagFilterAppliesToBothComponents() {
        assertThat(ids(service(0.5, 0.25).search(QUERY, List.of("Hostel")))).containsExactly(q2.getId());
        assertThat(service(0.5, 0.0).search(QUERY, List.of("Academics"))).extracting(SearchResult::questionId)
                .doesNotContain(q1.getId(), q2.getId());
        assertThat(ids(service(0.5, 0.25).search(QUERY, List.of("Academics", "Hostel")))).containsExactly(q2.getId());
    }

    @Test
    void commentVotesAndInterestAreNotPartOfTheScore() {
        var before = service(0.5, 0.0).search(QUERY, null);
        jdbc.update("update questions set interest_count = 900 where question_id = ?", q2.getId());
        em.flush();
        em.clear();
        var after = service(0.5, 0.0).search(QUERY, null);
        assertThat(ids(after)).isEqualTo(ids(before));
        for (int i = 0; i < before.size(); i++) {
            assertThat(after.get(i).relevance()).isEqualTo(before.get(i).relevance());
        }
    }

    @Test
    void maxResultsLimitsTheList() {
        var limited = new HybridSearchService(semanticScorer, lexicalScorer, assembler,
                properties.withSemanticWeight(0.5).withHybridThreshold(0.0).withMaxResults(1));
        assertThat(limited.search(QUERY, null)).hasSize(1);
    }

    @Test
    void placeholdersAreNeverGuessed() {
        assertThatThrownBy(() -> service(null, 0.2).search(QUERY, null)).isInstanceOf(SearchNotConfiguredException.class);
        assertThatThrownBy(() -> service(0.5, null).search(QUERY, null)).isInstanceOf(SearchNotConfiguredException.class);
        assertThatThrownBy(() -> service(1.5, 0.2).search(QUERY, null)).isInstanceOf(SearchNotConfiguredException.class);
        assertThatThrownBy(() -> service(-0.1, 0.2).search(QUERY, null)).isInstanceOf(SearchNotConfiguredException.class);
        assertThatThrownBy(() -> service(0.5, 1.2).search(QUERY, null)).isInstanceOf(SearchNotConfiguredException.class);
    }

    @Test
    void providerOutageFailsTheSearchInsteadOfSilentlyRankingByAnotherFormula() {
        fake.failing = true;
        assertThatThrownBy(() -> service(0.5, 0.2).search(QUERY, null)).isInstanceOf(EmbeddingException.class);
        assertThatThrownBy(() -> service(1.0, 0.2).search(QUERY, null)).isInstanceOf(EmbeddingException.class);
        assertThat(service(0.0, 0.2).search(QUERY, null)).isNotEmpty();      // lexical-only still works
    }
}
