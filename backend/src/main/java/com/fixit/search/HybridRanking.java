package com.fixit.search;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * HYBRID_SCORE = w * SEMANTIC + (1 - w) * LEXICAL, with w = SEMANTIC_WEIGHT.
 * A question missing from a component's map scores 0 for it (e.g. no embedding yet, or no shared words).
 * Semantic cosine is clamped to 0..1 first so a negative cosine cannot cancel lexical evidence.
 * A null map means "component not computed" (skipped because its weight is 0): its score is reported as null.
 * Pure function: no database, no configuration, no comment votes / interest counts - only the two scores.
 */
public final class HybridRanking {

    private HybridRanking() {
    }

    public static List<ScoredQuestion> combine(Map<Long, Double> semantic, Map<Long, Double> lexical, double semanticWeight) {
        double lexicalWeight = 1 - semanticWeight;
        Set<Long> ids = new LinkedHashSet<>();
        if (semantic != null) ids.addAll(semantic.keySet());
        if (lexical != null) ids.addAll(lexical.keySet());

        List<ScoredQuestion> combined = new ArrayList<>();
        for (Long id : ids) {
            Double sem = semantic == null ? null : clamp01(semantic.getOrDefault(id, 0.0));
            Double lex = lexical == null ? null : clamp01(lexical.getOrDefault(id, 0.0));
            double score = semanticWeight * (sem == null ? 0 : sem) + lexicalWeight * (lex == null ? 0 : lex);
            combined.add(new ScoredQuestion(id, score, sem, lex));
        }
        return combined;
    }

    private static double clamp01(double v) {
        return Math.max(0, Math.min(1, v));
    }
}
