package com.fixit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import com.fixit.embedding.EmbeddingPurpose;
import com.fixit.entity.Question;
import com.fixit.entity.User;
import com.fixit.repository.QuestionRepository;
import com.fixit.repository.UserRepository;
import com.fixit.search.QuestionEmbeddingService;
import com.fixit.search.SemanticScorer;
import com.fixit.support.FakeEmbeddings;

/** Stored questions are embedded as DOCUMENT, search text as QUERY (matters for asymmetric models like Jina v4). */
@SpringBootTest(properties = { FakeEmbeddings.P_PROVIDER, FakeEmbeddings.P_MODEL, FakeEmbeddings.P_URL,
        FakeEmbeddings.P_KEY, FakeEmbeddings.P_DIM, FakeEmbeddings.P_THRESHOLD })
@Import(FakeEmbeddings.Config.class)
@Transactional
class SearchPurposeTest {

    @Autowired FakeEmbeddings fake;
    @Autowired QuestionEmbeddingService embeddingService;
    @Autowired SemanticScorer scorer;
    @Autowired UserRepository users;
    @Autowired QuestionRepository questions;

    @BeforeEach
    void reset() {
        fake.reset();
    }

    @Test
    void indexingUsesDocument_searchingUsesQuery() {
        User author = users.save(new User("alice@smail.iitm.ac.in"));
        Question q = questions.save(new Question(author, "WiFi drops", "laptop disconnects"));
        embeddingService.refresh(q.getId(), q.getTitle(), q.getBody());
        assertThat(fake.purposes).containsExactly(EmbeddingPurpose.DOCUMENT);

        scorer.score("wifi dropping", null);
        assertThat(fake.purposes).containsExactly(EmbeddingPurpose.DOCUMENT, EmbeddingPurpose.QUERY);
    }
}
