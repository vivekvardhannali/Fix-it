package com.fixit.search;

/**
 * A question id with its final ranking {@code score}. {@code semanticScore} / {@code lexicalScore} are the component
 * scores that produced it (null when that component was not computed) - useful when tuning hybrid weights.
 */
public record ScoredQuestion(Long questionId, double score, Double semanticScore, Double lexicalScore) {
}
