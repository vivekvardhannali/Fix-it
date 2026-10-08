package com.fixit.search;

import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;

/** Lexical-only search: tag filter -> BM25 -> threshold (inclusive) -> descending order -> top N. */
@Service
public class LexicalSearchService {

    private final LexicalScorer scorer;
    private final SearchResultAssembler assembler;
    private final SearchProperties properties;

    public LexicalSearchService(LexicalScorer scorer, SearchResultAssembler assembler, SearchProperties properties) {
        this.scorer = scorer;
        this.assembler = assembler;
        this.properties = properties;
    }

    public List<SearchResult> search(String query, List<String> tagNames) {
        double threshold = properties.requireLexicalThreshold();
        List<ScoredQuestion> ranked = scorer.score(query, tagNames).entrySet().stream()
                .filter(e -> e.getValue() >= threshold)
                .map(e -> new ScoredQuestion(e.getKey(), e.getValue(), null, e.getValue()))
                .sorted(Comparator.comparingDouble(ScoredQuestion::score).reversed()
                        .thenComparing(ScoredQuestion::questionId))
                .limit(properties.maxResults())
                .toList();
        return assembler.assemble(ranked);
    }
}
