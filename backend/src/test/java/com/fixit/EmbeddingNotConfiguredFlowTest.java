package com.fixit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import com.fixit.dto.QuestionRequest;
import com.fixit.entity.User;
import com.fixit.repository.UserRepository;
import com.fixit.search.QuestionEmbeddingService;
import com.fixit.service.QuestionService;

/**
 * Shipped (placeholder) configuration, committed transactions: questions must still be created normally while the
 * embedding provider is unconfigured. Cleans up after itself.
 */
@SpringBootTest
class EmbeddingNotConfiguredFlowTest {

    @Autowired QuestionService questionService;
    @Autowired QuestionEmbeddingService embeddingService;
    @Autowired UserRepository users;
    @Autowired JdbcTemplate jdbc;

    private Long userId;
    private final List<Long> questionIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        questionIds.forEach(id -> {
            jdbc.update("delete from question_tags where question_id = ?", id);       // questions always have tags now
            jdbc.update("delete from questions where question_id = ?", id);
        });
        if (userId != null) {
            jdbc.update("delete from users where user_id = ?", userId);
        }
    }

    @Test
    void questionsStillWorkWithoutAProvider() {
        userId = users.save(new User("flow-unconfigured@smail.iitm.ac.in")).getId();
        long id = questionService.create(userId, new QuestionRequest("A title", "A body", List.of("tech"))).questionId();
        questionIds.add(id);
        questionService.update(userId, id, new QuestionRequest("A new title", "A body", null));

        assertThat(questionService.get(id).title()).isEqualTo("A new title");
        assertThat(jdbc.queryForObject("select count(*) from question_embeddings where question_id = ?", Integer.class, id)).isZero();

        var report = embeddingService.reindexAll();           // refuses cleanly, embeds nothing
        assertThat(report.embedded()).isZero();
        assertThat(jdbc.queryForObject("select count(*) from question_embeddings", Integer.class)).isZero();
    }
}
