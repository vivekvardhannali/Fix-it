package com.fixit.search;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.fixit.embedding.EmbeddingProperties;
import com.fixit.embedding.EmbeddingPurpose;
import com.fixit.embedding.EmbeddingService;

/**
 * Semantic component: cosine similarity between the query embedding and each candidate question's embedding.
 * Returns the score of EVERY candidate (no threshold, no limit) so hybrid ranking can combine components.
 * Questions without a current embedding are not candidates here.
 */
@Service
public class SemanticScorer {

    private final EmbeddingService embeddings;
    private final EmbeddingProperties properties;
    private final QuestionEmbeddingRepository repository;

    public SemanticScorer(EmbeddingService embeddings, EmbeddingProperties properties,
            QuestionEmbeddingRepository repository) {
        this.embeddings = embeddings;
        this.properties = properties;
        this.repository = repository;
    }

    /** @param tagNames restrict to questions having ANY of these tags; null/empty = all questions */
    public Map<Long, Double> score(String query, List<String> tagNames) {
        String text = SearchInput.query(query);
        List<String> tags = SearchInput.tags(tagNames);
        float[] queryVector = embeddings.generateEmbedding(text, EmbeddingPurpose.QUERY);   // EmbeddingException -> 503 when unavailable
        return repository.cosineScores(queryVector, properties.model(), properties.dimension(), tags);
    }
}
