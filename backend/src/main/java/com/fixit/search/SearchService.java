package com.fixit.search;

import java.util.List;

import org.springframework.stereotype.Service;

import com.fixit.service.TagCatalog;

/** Entry point for search (design: SearchService.searchQuestions(query, tags)). */
@Service
public class SearchService {

    private final HybridSearchService hybrid;
    private final SemanticSearchService semantic;
    private final LexicalSearchService lexical;
    private final TagCatalog catalog;

    public SearchService(HybridSearchService hybrid, SemanticSearchService semantic, LexicalSearchService lexical,
            TagCatalog catalog) {
        this.catalog = catalog;
        this.hybrid = hybrid;
        this.semantic = semantic;
        this.lexical = lexical;
    }

    public List<SearchResult> searchQuestions(SearchMode mode, String query, List<String> rawTags) {
        // selected tags must be from the fixed set (an unknown tag is a client mistake, not an empty result)
        List<String> tags = rawTags == null || rawTags.isEmpty() ? List.of() : catalog.requireKnown(rawTags);
        return switch (mode) {
            case HYBRID -> hybrid.search(query, tags);
            case SEMANTIC -> semantic.search(query, tags);
            case LEXICAL -> lexical.search(query, tags);
        };
    }
}
