package com.fixit.search;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

/**
 * Hybrid search (the intended final search): candidates -> semantic + lexical scores -> weighted mix -> threshold
 * (inclusive) -> descending order -> top N. Both the weight and the threshold are placeholders until tuned.
 * A component whose weight is 0 is not computed at all, so weight 0 works without an embedding provider and weight 1
 * never touches the lexical index. For any weight strictly between, a provider outage fails the search (503) rather
 * than silently ranking by a different formula.
 */
@Service
public class HybridSearchService {

    private final SemanticScorer semanticScorer;
    private final LexicalScorer lexicalScorer;
    private final SearchResultAssembler assembler;
    private final SearchProperties properties;

    public HybridSearchService(SemanticScorer semanticScorer, LexicalScorer lexicalScorer,
            SearchResultAssembler assembler, SearchProperties properties) {
        this.semanticScorer = semanticScorer;
        this.lexicalScorer = lexicalScorer;
        this.assembler = assembler;
        this.properties = properties;
    }

    public List<SearchResult> search(String query, List<String> tagNames) {
        double weight = properties.requireSemanticWeight();
        double threshold = properties.requireHybridThreshold();

        Map<Long, Double> semantic = weight > 0 ? semanticScorer.score(query, tagNames) : null;
        Map<Long, Double> lexical = weight < 1 ? lexicalScorer.score(query, tagNames) : null;

        List<ScoredQuestion> ranked = HybridRanking.combine(semantic, lexical, weight).stream()
                .filter(s -> s.score() >= threshold)
                .sorted(Comparator.comparingDouble(ScoredQuestion::score).reversed()
                        .thenComparing(ScoredQuestion::questionId))
                .limit(properties.maxResults())
                .toList();
        return assembler.assemble(ranked);
    }
}
