package com.fixit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fixit.search.HybridRanking;
import com.fixit.search.ScoredQuestion;

/** The hybrid formula alone (no database): HYBRID = w * SEMANTIC + (1 - w) * LEXICAL. */
class HybridRankingTest {

    private static List<Long> order(List<ScoredQuestion> scored) {
        return scored.stream().sorted(Comparator.comparingDouble(ScoredQuestion::score).reversed())
                .map(ScoredQuestion::questionId).toList();
    }

    private static ScoredQuestion of(List<ScoredQuestion> scored, long id) {
        return scored.stream().filter(s -> s.questionId() == id).findFirst().orElseThrow();
    }

    // semantic prefers question 1, lexical prefers question 2
    static final Map<Long, Double> SEM = Map.of(1L, 0.9, 2L, 0.5);
    static final Map<Long, Double> LEX = Map.of(1L, 0.1, 2L, 0.9);

    @Test
    void formulaIsTheWeightedSum() {
        var scored = HybridRanking.combine(SEM, LEX, 0.7);
        assertThat(of(scored, 1).score()).isCloseTo(0.7 * 0.9 + 0.3 * 0.1, within(1e-12));
        assertThat(of(scored, 2).score()).isCloseTo(0.7 * 0.5 + 0.3 * 0.9, within(1e-12));
        assertThat(of(scored, 1).semanticScore()).isEqualTo(0.9);
        assertThat(of(scored, 1).lexicalScore()).isEqualTo(0.1);
    }

    @Test
    void differentWeightsChangeTheOrder() {
        assertThat(order(HybridRanking.combine(SEM, LEX, 0.9))).containsExactly(1L, 2L);   // mostly semantic
        assertThat(order(HybridRanking.combine(SEM, LEX, 0.1))).containsExactly(2L, 1L);   // mostly lexical
        assertThat(order(HybridRanking.combine(SEM, LEX, 1.0))).containsExactly(1L, 2L);   // semantic only
        assertThat(order(HybridRanking.combine(SEM, LEX, 0.0))).containsExactly(2L, 1L);   // lexical only
    }

    @Test
    void extremesEqualTheSingleComponent() {
        assertThat(of(HybridRanking.combine(SEM, LEX, 1.0), 2).score()).isEqualTo(0.5);
        assertThat(of(HybridRanking.combine(SEM, LEX, 0.0), 2).score()).isEqualTo(0.9);
    }

    @Test
    void aQuestionMissingFromOneComponentScoresZeroThere() {
        var scored = HybridRanking.combine(Map.of(1L, 0.8), Map.of(2L, 0.6), 0.5);
        assertThat(scored).hasSize(2);                                                       // union of both
        assertThat(of(scored, 1).score()).isCloseTo(0.4, within(1e-12));                     // no lexical match
        assertThat(of(scored, 1).lexicalScore()).isEqualTo(0.0);
        assertThat(of(scored, 2).score()).isCloseTo(0.3, within(1e-12));                     // no embedding
        assertThat(of(scored, 2).semanticScore()).isEqualTo(0.0);
    }

    @Test
    void negativeCosineIsClampedSoItCannotCancelLexicalEvidence() {
        var scored = HybridRanking.combine(Map.of(1L, -0.4), Map.of(1L, 0.5), 0.5);
        assertThat(of(scored, 1).score()).isCloseTo(0.25, within(1e-12));
        assertThat(of(scored, 1).semanticScore()).isEqualTo(0.0);
    }

    @Test
    void aSkippedComponentIsReportedAsNull() {
        var lexicalOnly = HybridRanking.combine(null, LEX, 0.0);
        assertThat(of(lexicalOnly, 1).semanticScore()).isNull();
        var semanticOnly = HybridRanking.combine(SEM, null, 1.0);
        assertThat(of(semanticOnly, 1).lexicalScore()).isNull();
        assertThat(HybridRanking.combine(Map.of(), Map.of(), 0.5)).isEmpty();
    }
}
