package com.fixit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import com.fixit.dto.QuestionRequest;
import com.fixit.entity.User;
import com.fixit.repository.UserRepository;
import com.fixit.search.QuestionEmbeddingService;
import com.fixit.search.ReindexReport;
import com.fixit.service.QuestionService;
import com.fixit.support.FakeEmbeddings;

/**
 * NOT @Transactional on purpose: the embedding hook runs AFTER COMMIT, which rolled-back tests never reach.
 * Cleans up after itself; if it ever dies midway, delete leftover `flow-*@smail.iitm.ac.in` users and their rows.
 */
@SpringBootTest(properties = { FakeEmbeddings.P_PROVIDER, FakeEmbeddings.P_MODEL, FakeEmbeddings.P_URL,
        FakeEmbeddings.P_KEY, FakeEmbeddings.P_DIM, FakeEmbeddings.P_THRESHOLD })
@Import(FakeEmbeddings.Config.class)
class EmbeddingCommittedFlowTest {

    @Autowired QuestionService questionService;
    @Autowired QuestionEmbeddingService embeddingService;
    @Autowired UserRepository users;
    @Autowired FakeEmbeddings fake;
    @Autowired JdbcTemplate jdbc;

    private Long userId;
    private final List<Long> questionIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        fake.reset();
        userId = users.save(new User("flow-author@smail.iitm.ac.in")).getId();
    }

    @AfterEach
    void cleanUp() {
        for (Long id : questionIds) {
            jdbc.update("delete from question_embeddings where question_id = ?", id);
            jdbc.update("delete from question_tags where question_id = ?", id);
            jdbc.update("delete from questions where question_id = ?", id);
        }
        jdbc.update("delete from tags where lower(name) like 'flowtag%'");
        jdbc.update("delete from users where user_id = ?", userId);
    }

    private long create(String title, String body) {
        long id = questionService.create(userId, new QuestionRequest(title, body, List.of("tech"))).questionId();
        questionIds.add(id);
        return id;
    }

    private int embeddingRows(long id) {
        return jdbc.queryForObject("select count(*) from question_embeddings where question_id = ?", Integer.class, id);
    }

    private String vector(long id) {
        return jdbc.queryForObject("select embedding::text from question_embeddings where question_id = ?", String.class, id);
    }

    @Test
    void creatingAQuestionEmbedsItAfterCommit_andEditingTheTextReEmbeds() {
        long id = create("WiFi drops in hostel", "laptop loses connection every hour");
        assertThat(embeddingRows(id)).isEqualTo(1);
        String before = vector(id);

        questionService.update(userId, id, new QuestionRequest("Mess food quality", "dinner served cold", null));
        assertThat(vector(id)).isNotEqualTo(before);

        int calls = fake.calls.get();                       // tags-only edit: same text -> no provider call
        questionService.update(userId, id, new QuestionRequest("Mess food quality", "dinner served cold", List.of("math")));
        assertThat(fake.calls.get()).isEqualTo(calls);
    }

    @Test
    void providerDownDoesNotBlockQuestionCreation_andReindexBackfillsLater() {
        fake.failing = true;
        long id1 = create("First question about wifi", "body one");
        long id2 = create("Second question about mess", "body two");
        assertThat(embeddingRows(id1)).isZero();             // question exists, embedding missing
        assertThat(jdbc.queryForObject("select count(*) from questions where question_id in (?, ?)", Integer.class, id1, id2))
                .isEqualTo(2);

        fake.failing = false;                                // provider recovers
        ReindexReport first = embeddingService.reindexAll();
        assertThat(embeddingRows(id1)).isEqualTo(1);
        assertThat(embeddingRows(id2)).isEqualTo(1);
        assertThat(first.embedded()).isGreaterThanOrEqualTo(2);

        ReindexReport second = embeddingService.reindexAll(); // idempotent: nothing left to do
        assertThat(second.embedded()).isZero();
        assertThat(second.failed()).isZero();
    }
}
