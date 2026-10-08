package com.fixit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import com.fixit.exception.BadRequestException;
import com.fixit.repository.QuestionRepository;
import com.fixit.repository.QuestionTagRepository;
import com.fixit.repository.TagRepository;
import com.fixit.repository.UserRepository;
import com.fixit.search.*;
import com.fixit.service.TagService;
import com.fixit.support.FakeEmbeddings;

import jakarta.persistence.EntityManager;

/**
 * Checkpoint 16 - search MECHANICS with the test-only fake provider (shared-word vectors). It proves tag filtering,
 * thresholding and ordering work; it cannot prove semantic quality, which needs the real model.
 *
 * Data (cosine with the query "wifi disconnecting hostel laptop", hand-checked: Q1 ~0.75, Q2 ~0.69, others ~0):
 *   Q1 wifi, Networking | Q2 near-duplicate of Q1, Networking+Hostel | Q3 electives, Academics | Q4 mess, Mess
 */
@SpringBootTest(properties = { FakeEmbeddings.P_PROVIDER, FakeEmbeddings.P_MODEL, FakeEmbeddings.P_URL,
        FakeEmbeddings.P_KEY, FakeEmbeddings.P_DIM, FakeEmbeddings.P_THRESHOLD })
@Import(FakeEmbeddings.Config.class)
@Transactional
class SemanticSearchTest {

    static final String QUERY = "wifi disconnecting hostel laptop";

    @Autowired SemanticSearchService search;
    @Autowired SemanticScorer scorer;
    @Autowired SearchResultAssembler assembler;
    @Autowired SearchProperties properties;
    @Autowired QuestionEmbeddingService embeddingService;
    @Autowired com.fixit.embedding.EmbeddingProperties embeddingProperties;
    @Autowired FakeEmbeddings fake;
    @Autowired UserRepository users;
    @Autowired QuestionRepository questions;
    @Autowired TagRepository tags;
    @Autowired TagService tagService;
    @Autowired QuestionTagRepository questionTags;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;

    Question q1, q2, q3, q4;

    @BeforeEach
    void setUp() {
        fake.reset();
        User author = users.save(new User("alice@smail.iitm.ac.in"));
        q1 = make(author, "WiFi keeps disconnecting in hostel",
                "my laptop loses wifi connection every hour in hostel block", "Networking");
        q2 = make(author, "Cannot connect to hostel WiFi",
                "laptop wifi disconnects hostel connection", "Networking", "Hostel");
        q3 = make(author, "How to register for electives",
                "course registration electives deadline portal", "Academics");
        q4 = make(author, "Mess food quality complaint", "dinner served cold and tasteless", "Mess");
        em.flush();
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

    private List<Long> ids(List<SearchResult> results) {
        return results.stream().map(SearchResult::questionId).toList();
    }

    private SemanticSearchService withThreshold(Double threshold, Integer maxResults) {
        return new SemanticSearchService(scorer, assembler,
                properties.withSemanticThreshold(threshold).withMaxResults(maxResults));
    }

    @Test
    void relevantReturned_irrelevantFiltered_higherSimilarityFirst() {
        var results = search.search(QUERY, null);
        assertThat(ids(results)).containsExactly(q1.getId(), q2.getId());        // Q3, Q4 filtered by threshold
        assertThat(results.get(0).relevance()).isGreaterThan(results.get(1).relevance());
        assertThat(results.get(0).relevance()).isBetween(0.70, 0.80);
        assertThat(results).allSatisfy(r -> assertThat(r.relevance()).isGreaterThanOrEqualTo(0.3));
    }

    @Test
    void belowThresholdIsNotReturned_andTheThresholdDecidesTheCutoff() {
        assertThat(ids(withThreshold(0.72, null).search(QUERY, null))).containsExactly(q1.getId());   // Q2 (~0.69) now out
        assertThat(ids(withThreshold(0.99, null).search(QUERY, null))).isEmpty();
        assertThat(ids(withThreshold(-1.0, null).search(QUERY, null))).hasSize(4);                    // everything passes
    }

    @Test
    void queryWithNoSuitableMatchReturnsNothing() {
        assertThat(search.search("quantum chromodynamics lagrangian", null)).isEmpty();
    }

    @Test
    void duplicatesScoreEquallyAndTieBreakByIdDeterministically() {
        User author = users.findById(q1.getAuthor().getId()).orElseThrow();
        Question twin = make(author, "WiFi keeps disconnecting in hostel",
                "my laptop loses wifi connection every hour in hostel block", "Networking");
        var results = search.search(QUERY, null);
        assertThat(ids(results)).containsExactly(q1.getId(), twin.getId(), q2.getId());
        assertThat(results.get(0).relevance()).isEqualTo(results.get(1).relevance());
    }

    @Test
    void tagFilterRestrictsCandidates_unionOfSelectedTags_caseInsensitive() {
        assertThat(ids(search.search(QUERY, List.of("networking")))).containsExactly(q1.getId(), q2.getId());
        assertThat(ids(search.search(QUERY, List.of("HOSTEL")))).containsExactly(q2.getId());       // only Q2 has Hostel
        assertThat(ids(search.search(QUERY, List.of("Academics")))).isEmpty();                      // Q3 has the tag but is irrelevant
        assertThat(ids(search.search(QUERY, List.of("Academics", "Hostel")))).containsExactly(q2.getId()); // union, not intersection
        assertThat(ids(search.search(QUERY, List.of("  hostel ", "Hostel")))).containsExactly(q2.getId());
        assertThat(search.search(QUERY, List.of("NoSuchTag"))).isEmpty();
        // a relevant question outside the selected tags is excluded even though it matches the text
        assertThat(ids(search.search("electives registration deadline", List.of("Networking")))).isEmpty();
        assertThat(ids(search.search("electives registration deadline", List.of("Academics")))).containsExactly(q3.getId());
    }

    @Test
    void resultCarriesWhatTheResultListNeeds() {
        jdbc.update("update questions set interest_count = 7, is_resolved = true where question_id = ?", q1.getId());
        em.clear();
        SearchResult r = search.search(QUERY, null).get(0);
        assertThat(r.questionId()).isEqualTo(q1.getId());
        assertThat(r.title()).isEqualTo("WiFi keeps disconnecting in hostel");
        assertThat(r.bodyPreview()).isEqualTo("my laptop loses wifi connection every hour in hostel block");
        assertThat(r.tags()).containsExactly("Networking");
        assertThat(r.isResolved()).isTrue();
        assertThat(r.interestCount()).isEqualTo(7);
    }

    @Test
    void bodyPreviewIsAFewLinesAndShortened() {
        assertThat(SearchResultAssembler.preview("l1\n\n  l2  \nl3\nl4\nl5")).isEqualTo("l1\nl2\nl3");
        String preview = SearchResultAssembler.preview("x".repeat(500));
        assertThat(preview).hasSize(201).endsWith("…");
    }

    @Test
    void interestAndCommentVotesDoNotChangeRelevanceOrOrder() {
        var before = search.search(QUERY, null);
        jdbc.update("update questions set interest_count = 500 where question_id = ?", q2.getId());   // Q2 hugely popular
        em.clear();
        var after = search.search(QUERY, null);
        assertThat(ids(after)).isEqualTo(ids(before));                         // Q1 still first
        assertThat(after.get(1).relevance()).isEqualTo(before.get(1).relevance());
        assertThat(after.get(1).interestCount()).isEqualTo(500);
    }

    @Test
    void maxResultsLimitsTheList() {
        assertThat(withThreshold(-1.0, 2).search(QUERY, null)).hasSize(2);
    }

    @Test
    void questionsWithoutACurrentEmbeddingAreNotCandidates() {
        jdbc.update("delete from question_embeddings where question_id = ?", q1.getId());                    // never embedded
        jdbc.update("update question_embeddings set model = 'old-model' where question_id = ?", q2.getId()); // other model
        assertThat(search.search(QUERY, null)).isEmpty();
        embeddingService.refresh(q1.getId(), q1.getTitle(), q1.getBody());                                  // re-embedding fixes it
        embeddingService.refresh(q2.getId(), q2.getTitle(), q2.getBody());
        assertThat(ids(search.search(QUERY, null))).containsExactly(q1.getId(), q2.getId());
    }

    @Test
    void invalidRequestsAndUnavailableDependencies() {
        assertThatThrownBy(() -> search.search("  ", null)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> search.search(null, null)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> search.search(QUERY, List.of("ok", " "))).isInstanceOf(BadRequestException.class);

        fake.failing = true;
        assertThatThrownBy(() -> search.search(QUERY, null)).isInstanceOf(EmbeddingException.class);
        fake.failing = false;

        // threshold still a placeholder (null) or out of range -> refuses instead of guessing
        assertThatThrownBy(() -> withThreshold(null, null).search(QUERY, null)).isInstanceOf(SearchNotConfiguredException.class);
        assertThatThrownBy(() -> withThreshold(1.5, null).search(QUERY, null)).isInstanceOf(SearchNotConfiguredException.class);
    }
}
