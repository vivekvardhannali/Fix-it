package com.fixit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.fixit.entity.Question;
import com.fixit.entity.User;
import com.fixit.repository.QuestionRepository;
import com.fixit.repository.UserRepository;
import com.fixit.search.QuestionEmbeddingService;
import com.fixit.search.QuestionEmbeddingService.Outcome;
import com.fixit.support.FakeEmbeddings;

import jakarta.persistence.EntityManager;

/** Checkpoint 15 (mechanics, with the test-only fake provider). */
@SpringBootTest(properties = { FakeEmbeddings.P_PROVIDER, FakeEmbeddings.P_MODEL, FakeEmbeddings.P_URL,
        FakeEmbeddings.P_KEY, FakeEmbeddings.P_DIM, FakeEmbeddings.P_THRESHOLD })
@Import(FakeEmbeddings.Config.class)
@Transactional
class QuestionEmbeddingTest {

    @Autowired QuestionEmbeddingService service;
    @Autowired FakeEmbeddings fake;
    @Autowired UserRepository users;
    @Autowired QuestionRepository questions;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;

    User author;

    @BeforeEach
    void setUp() {
        fake.reset();
        author = users.save(new User("alice@smail.iitm.ac.in"));
    }

    private Question question(String title, String body) {
        Question q = questions.save(new Question(author, title, body));
        em.flush();
        return q;
    }

    private String storedVector(long id) {
        return jdbc.queryForObject("select embedding::text from question_embeddings where question_id = ?", String.class, id);
    }

    private double cosineDistance(long a, long b) {
        return jdbc.queryForObject("select x.embedding <=> y.embedding from question_embeddings x, question_embeddings y "
                + "where x.question_id = ? and y.question_id = ?", Double.class, a, b);
    }

    @Test
    void vectorIsProducedWithCorrectDimensionAndStored() {
        Question q = question("WiFi drops", "laptop disconnects from hostel wifi");
        assertThat(service.refresh(q.getId(), q.getTitle(), q.getBody())).isEqualTo(Outcome.EMBEDDED);

        assertThat(jdbc.queryForObject("select vector_dims(embedding) from question_embeddings where question_id = ?",
                Integer.class, q.getId())).isEqualTo(FakeEmbeddings.DIM);
        assertThat(jdbc.queryForObject("select model from question_embeddings where question_id = ?", String.class, q.getId()))
                .isEqualTo(FakeEmbeddings.MODEL);
        assertThat(jdbc.queryForObject("select count(*) from question_embeddings", Integer.class)).isEqualTo(1);
    }

    @Test
    void differentMeaningsGetDifferentVectors_similarOnesCloseTogether() {
        Question wifi1 = question("WiFi keeps disconnecting", "laptop loses wifi connection in hostel");
        Question wifi2 = question("Hostel wifi disconnects", "laptop wifi connection drops in hostel");
        Question mess = question("Mess food quality", "dinner served cold and tasteless in mess");
        for (Question q : new Question[] { wifi1, wifi2, mess }) {
            service.refresh(q.getId(), q.getTitle(), q.getBody());
        }
        assertThat(cosineDistance(wifi1.getId(), wifi2.getId())).isLessThan(cosineDistance(wifi1.getId(), mess.getId()));
        assertThat(cosineDistance(wifi1.getId(), mess.getId())).isGreaterThan(0.9);
        assertThat(storedVector(wifi1.getId())).isNotEqualTo(storedVector(mess.getId()));
    }

    @Test
    void updatingTheText_updatesTheStoredVector_butSameTextDoesNotCallTheProvider() {
        Question q = question("WiFi drops", "laptop disconnects from hostel wifi");
        service.refresh(q.getId(), q.getTitle(), q.getBody());
        String before = storedVector(q.getId());
        String hashBefore = jdbc.queryForObject("select text_hash from question_embeddings where question_id = ?", String.class, q.getId());
        int callsBefore = fake.calls.get();

        assertThat(service.refresh(q.getId(), q.getTitle(), q.getBody())).isEqualTo(Outcome.UP_TO_DATE);
        assertThat(fake.calls.get()).isEqualTo(callsBefore);               // nothing re-sent

        assertThat(service.refresh(q.getId(), "Mess food", "dinner served cold")).isEqualTo(Outcome.EMBEDDED);
        assertThat(fake.calls.get()).isEqualTo(callsBefore + 1);
        assertThat(storedVector(q.getId())).isNotEqualTo(before);
        assertThat(jdbc.queryForObject("select text_hash from question_embeddings where question_id = ?", String.class, q.getId()))
                .isNotEqualTo(hashBefore);
        assertThat(jdbc.queryForObject("select count(*) from question_embeddings", Integer.class)).isEqualTo(1); // updated, not duplicated
    }

    @Test
    void vectorFromAnotherModelIsReplaced() {
        Question q = question("WiFi drops", "laptop disconnects");
        service.refresh(q.getId(), q.getTitle(), q.getBody());
        jdbc.update("update question_embeddings set model = 'some-old-model' where question_id = ?", q.getId());
        assertThat(service.refresh(q.getId(), q.getTitle(), q.getBody())).isEqualTo(Outcome.EMBEDDED);
        assertThat(jdbc.queryForObject("select model from question_embeddings where question_id = ?", String.class, q.getId()))
                .isEqualTo(FakeEmbeddings.MODEL);
    }

    @Test
    void wrongSizedVectorIsNeverStored() {
        Question q = question("WiFi drops", "laptop disconnects");
        fake.forcedLength = 10;
        assertThat(service.refresh(q.getId(), q.getTitle(), q.getBody())).isEqualTo(Outcome.FAILED);
        assertThat(jdbc.queryForObject("select count(*) from question_embeddings", Integer.class)).isZero();
    }

    @Test
    void providerFailureIsReportedNotThrown_andLeavesOldVectorUntouched() {
        Question q = question("WiFi drops", "laptop disconnects");
        service.refresh(q.getId(), q.getTitle(), q.getBody());
        String before = storedVector(q.getId());
        fake.failing = true;
        assertThat(service.refresh(q.getId(), "A brand new title", "new body")).isEqualTo(Outcome.FAILED);
        assertThat(storedVector(q.getId())).isEqualTo(before);
    }
}
