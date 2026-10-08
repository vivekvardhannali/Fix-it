package com.fixit.search;

import java.util.List;

/**
 * What the search-result list needs per question (design 1.6). {@code relevance} is the final ranking score;
 * the two component scores are null when that component was not computed.
 */
public record SearchResult(
        Long questionId,
        String title,
        String bodyPreview,
        List<String> tags,
        boolean isResolved,
        int interestCount,
        double relevance,
        Double semanticScore,
        Double lexicalScore) {
}
