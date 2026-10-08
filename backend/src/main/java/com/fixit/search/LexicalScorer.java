package com.fixit.search;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

/**
 * Lexical component: normalised BM25 (0..1) of every question sharing at least one term with the query.
 * Needs no embedding provider, and covers every question (also ones without a vector).
 */
@Service
public class LexicalScorer {

    private final Bm25Index index;
    private final LexicalCorpusRepository corpus;
    private final SearchProperties properties;

    public LexicalScorer(Bm25Index index, LexicalCorpusRepository corpus, SearchProperties properties) {
        this.index = index;
        this.corpus = corpus;
        this.properties = properties;
    }

    /** @param tagNames restrict to questions having ANY of these tags; null/empty = all questions */
    public Map<Long, Double> score(String query, List<String> tagNames) {
        String text = SearchInput.query(query);
        List<String> tags = SearchInput.tags(tagNames);
        List<String> terms = Tokenizer.tokens(text);
        if (terms.isEmpty()) {
            return Map.of();                    // e.g. only stop words / punctuation: nothing to match on
        }
        Map<Long, Double> scores = new HashMap<>(index.score(terms, properties.bm25K1(), properties.bm25B()));
        if (!tags.isEmpty()) {
            Set<Long> allowed = corpus.idsWithAnyTag(tags);
            scores.keySet().retainAll(allowed);
        }
        return scores;
    }
}
