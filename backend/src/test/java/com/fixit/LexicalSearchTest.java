package com.fixit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.fixit.embedding.EmbeddingProperties;
import com.fixit.entity.Question;
import com.fixit.entity.QuestionTag;
import com.fixit.entity.User;
import com.fixit.exception.BadRequestException;
import com.fixit.repository.QuestionRepository;
import com.fixit.repository.QuestionTagRepository;
import com.fixit.repository.UserRepository;
import com.fixit.search.*;
import com.fixit.service.TagService;

import jakarta.persistence.EntityManager;

/**
 * Checkpoint 17: lexical (BM25) search on its own. Deliberately runs with the SHIPPED config, i.e. NO embedding
 * provider and no embeddings stored - lexical search must not depend on them.
 */
@SpringBootTest(properties = "app.search.lexical-threshold=0.2")
@Transactional
class LexicalSearchTest {

    static final String QUERY = "wifi disconnecting hostel laptop";

    @Autowired LexicalSearchService search;
    @Autowired LexicalScorer scorer;
    @Autowired SearchResultAssembler assembler;
    @Autowired SearchProperties properties;
    @Autowired EmbeddingProperties embeddingProperties;
    @Autowired UserRepository users;
    @Autowired QuestionRepository questions;
    @Autowired QuestionTagRepository questionTags;
    @Autowired TagService tagService;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;

    User author;
    Question q1, q2, q3, q4;

    @BeforeEach
    void setUp() {
        author = users.save(new User("alice@smail.iitm.ac.in"));
        q1 = make("WiFi keeps disconnecting in hostel", "my laptop loses wifi connection every hour in hostel block", "Networking");
        q2 = make("Cannot connect to hostel WiFi", "laptop wifi disconnects hostel connection", "Networking", "Hostel");
        q3 = make("How to register for electives", "course registration electives deadline portal", "Academics");
        q4 = make("Mess food quality complaint", "dinner served cold and tasteless", "Mess");
        em.flush();
    }

    private Question make(String title, String body, String... tagNames) {
        Question q = questions.save(new Question(author, title, body));
        for (String t : tagNames) {
            questionTags.save(new QuestionTag(q, tagService.findOrCreate(t)));
        }
        em.flush();
        return q;
    }

    private List<Long> ids(List<SearchResult> results) {
        return results.stream().map(SearchResult::questionId).toList();
    }

    private LexicalSearchService withThreshold(Double t, Integer max) {
        return new LexicalSearchService(scorer, assembler, properties.withLexicalThreshold(t).withMaxResults(max));
    }

    @Test
    void runsWithoutAnyEmbeddingProvider() {
        assertThat(embeddingProperties.isConfigured()).isFalse();
        assertThat(jdbc.queryForObject("select count(*) from question_embeddings", Integer.class)).isZero();
        assertThat(search.search(QUERY, null)).isNotEmpty();
    }

    @Test
    void scoresAreNormalisedAndOnlyQuestionsSharingTermsAreScored() {
        Map<Long, Double> scores = scorer.score(QUERY, null);
        assertThat(scores.keySet()).containsExactlyInAnyOrder(q1.getId(), q2.getId());   // Q3, Q4 share no term
        assertThat(scores.values()).allSatisfy(s -> assertThat(s).isStrictlyBetween(0.0, 1.0));
    }

    @Test
    void relevantReturned_irrelevantFiltered_betterMatchFirst() {
        var results = search.search(QUERY, null);
        assertThat(ids(results)).containsExactly(q1.getId(), q2.getId());      // Q1 has all 4 terms, Q2 lacks "disconnecting"
        assertThat(results.get(0).relevance()).isGreaterThan(results.get(1).relevance());
        assertThat(results).allSatisfy(r -> {
            assertThat(r.relevance()).isGreaterThanOrEqualTo(0.2);
            assertThat(r.lexicalScore()).isEqualTo(r.relevance());
            assertThat(r.semanticScore()).isNull();
        });
    }

    @Test
    void noSuitableMatchOrNothingToMatchOnReturnsNothing() {
        assertThat(search.search("quantum chromodynamics", null)).isEmpty();
        assertThat(search.search("the and of ?!", null)).isEmpty();               // only stop words / punctuation
    }

    @Test
    void thresholdDecidesTheCutoff() {
        assertThat(ids(withThreshold(0.0, null).search(QUERY, null))).containsExactly(q1.getId(), q2.getId());
        assertThat(withThreshold(0.99, null).search(QUERY, null)).isEmpty();
        double q2Score = scorer.score(QUERY, null).get(q2.getId());
        assertThat(ids(withThreshold(q2Score, null).search(QUERY, null))).contains(q2.getId());          // inclusive (>=)
        assertThat(ids(withThreshold(q2Score + 0.0001, null).search(QUERY, null))).doesNotContain(q2.getId());
    }

    @Test
    void rareTermsCountMoreThanCommonOnes() {                                    // inverse document frequency
        make("Router firmware update", "flash the router");                     // only doc with "firmware"
        make("Wifi password", "where to find wifi password");
        make("Wifi speed", "wifi is slow");
        var scores = scorer.score("wifi firmware", null);
        long firmwareDoc = questions.findAll().stream().filter(q -> q.getTitle().contains("firmware")).findFirst().orElseThrow().getId();
        long wifiOnlyDoc = questions.findAll().stream().filter(q -> q.getTitle().equals("Wifi speed")).findFirst().orElseThrow().getId();
        assertThat(scores.get(firmwareDoc)).isGreaterThan(scores.get(wifiOnlyDoc));
    }

    @Test
    void repeatingAWordHasDiminishingReturns_andShortDocumentsBeatLongOnesForTheSameHit() {
        Question spam = make("Router", "router router router router router router router router");
        Question varied = make("Router switch", "router switch cable port vlan gateway modem");
        Map<Long, Double> scores = scorer.score("router switch", null);
        assertThat(scores.get(varied.getId())).isGreaterThan(scores.get(spam.getId()));        // saturation

        Question shortDoc = make("Printer jam", "stuck");
        Question longDoc = make("Printer help", "stuck " + "lorem ipsum dolor sit amet consectetur adipiscing elit ".repeat(10));
        Map<Long, Double> s2 = scorer.score("stuck", null);
        assertThat(s2.get(shortDoc.getId())).isGreaterThan(s2.get(longDoc.getId()));            // length normalisation
    }

    @Test
    void caseAndPunctuationDoNotMatter() {
        assertThat(scorer.score("WIFI!!!, Hostel?", null)).isEqualTo(scorer.score("wifi hostel", null));
    }

    @Test
    void tagFilterRestrictsCandidates_anyOfTheSelectedTags() {
        assertThat(ids(search.search(QUERY, List.of("networking")))).containsExactly(q1.getId(), q2.getId());
        assertThat(ids(search.search(QUERY, List.of("HOSTEL")))).containsExactly(q2.getId());
        assertThat(search.search(QUERY, List.of("Academics"))).isEmpty();
        assertThat(ids(search.search(QUERY, List.of("Academics", "Hostel")))).containsExactly(q2.getId());   // union
        assertThat(search.search(QUERY, List.of("NoSuchTag"))).isEmpty();
        assertThat(search.search("electives registration", List.of("Networking"))).isEmpty();
        assertThat(ids(search.search("electives registration", List.of("Academics")))).containsExactly(q3.getId());
    }

    @Test
    void interestCountDoesNotChangeScoresOrOrder() {
        var before = search.search(QUERY, null);
        jdbc.update("update questions set interest_count = 500 where question_id = ?", q2.getId());
        em.clear();
        var after = search.search(QUERY, null);
        assertThat(ids(after)).isEqualTo(ids(before));
        assertThat(after.get(1).relevance()).isEqualTo(before.get(1).relevance());
        assertThat(after.get(1).interestCount()).isEqualTo(500);
    }

    @Test
    void indexFollowsEditsAndNewQuestions() {
        assertThat(ids(search.search("wifi", null))).doesNotContain(q4.getId());
        q4.setTitle("Mess wifi is down");
        q4.touch();                                  // the application always touches updated_at on text edits
        em.flush();
        assertThat(ids(search.search("wifi", null))).contains(q4.getId());

        Question fresh = make("Brand new wifi question", "wifi wifi");
        assertThat(ids(search.search("wifi", null))).contains(fresh.getId());
    }

    @Test
    void resultCarriesWhatTheResultListNeeds() {
        jdbc.update("update questions set interest_count = 3, is_resolved = true where question_id = ?", q1.getId());
        em.clear();
        SearchResult r = search.search(QUERY, null).get(0);
        assertThat(r.questionId()).isEqualTo(q1.getId());
        assertThat(r.title()).isEqualTo("WiFi keeps disconnecting in hostel");
        assertThat(r.bodyPreview()).startsWith("my laptop loses wifi");
        assertThat(r.tags()).containsExactly("Networking");
        assertThat(r.isResolved()).isTrue();
        assertThat(r.interestCount()).isEqualTo(3);
    }

    @Test
    void maxResultsLimitsTheList() {
        assertThat(withThreshold(0.0, 1).search(QUERY, null)).hasSize(1);
    }

    @Test
    void invalidRequestsAndUnsetThreshold() {
        assertThatThrownBy(() -> search.search("  ", null)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> search.search(QUERY, List.of(" "))).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> withThreshold(null, null).search(QUERY, null)).isInstanceOf(SearchNotConfiguredException.class);
        assertThatThrownBy(() -> withThreshold(1.5, null).search(QUERY, null)).isInstanceOf(SearchNotConfiguredException.class);
        assertThatThrownBy(() -> withThreshold(-0.1, null).search(QUERY, null)).isInstanceOf(SearchNotConfiguredException.class);
    }
}
