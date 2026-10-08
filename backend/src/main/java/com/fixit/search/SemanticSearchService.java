package com.fixit.search;

import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;

/**
 * Semantic-only search: tag filter -> cosine similarity -> threshold -> descending order -> top N.
 * The threshold is a placeholder until tuned (SEARCH_SEMANTIC_THRESHOLD); the threshold is inclusive (>=).
 */
@Service
public class SemanticSearchService {

    private final SemanticScorer scorer;
    private final SearchResultAssembler assembler;
    private final SearchProperties properties;

    public SemanticSearchService(SemanticScorer scorer, SearchResultAssembler assembler, SearchProperties properties) {
        this.scorer = scorer;
        this.assembler = assembler;
        this.properties = properties;
    }

    public List<SearchResult> search(String query, List<String> tagNames) {
        double threshold = properties.requireSemanticThreshold();
        List<ScoredQuestion> ranked = scorer.score(query, tagNames).entrySet().stream()
                .filter(e -> e.getValue() >= threshold)
                .map(e -> new ScoredQuestion(e.getKey(), e.getValue(), e.getValue(), null))
                .sorted(Comparator.comparingDouble(ScoredQuestion::score).reversed()
                        .thenComparing(ScoredQuestion::questionId))
                .limit(properties.maxResults())
                .toList();
        return assembler.assemble(ranked);
    }
}
