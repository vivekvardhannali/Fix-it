package com.fixit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import com.fixit.entity.*;
import com.fixit.repository.*;

import jakarta.persistence.EntityManager;

/**
 * Checkpoint 4: Spring Boot -> JPA -> PostgreSQL. Each test is rolled back, so the DB stays empty.
 */
@SpringBootTest
@Transactional
class JpaMappingTest {

    @Autowired UserRepository users;
    @Autowired QuestionRepository questions;
    @Autowired TagRepository tags;
    @Autowired QuestionTagRepository questionTags;
    @Autowired AnswerRepository answers;
    @Autowired QuestionCommentRepository comments;
    @Autowired QuestionCommentVoteRepository votes;
    @Autowired QuestionInterestRepository interests;
    @Autowired NotificationRepository notifications;
    @Autowired EntityManager em;

    private void flushAndClear() {
        em.flush();
        em.clear();
    }

    @Test
    void createAndReadUser() {
        Long id = users.save(new User("alice@smail.iitm.ac.in")).getId();
        flushAndClear();

        User found = users.findById(id).orElseThrow();
        assertThat(found.getSmail()).isEqualTo("alice@smail.iitm.ac.in");
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(users.findBySmail("alice@smail.iitm.ac.in")).isPresent();
    }

    @Test
    void createAndReadQuestionBack() {
        User alice = users.save(new User("alice@smail.iitm.ac.in"));
        Long id = questions.save(new Question(alice, "WiFi drops", "Disconnects often")).getId();
        flushAndClear();

        Question q = questions.findById(id).orElseThrow();
        assertThat(q.getTitle()).isEqualTo("WiFi drops");
        assertThat(q.getAuthor().getSmail()).isEqualTo("alice@smail.iitm.ac.in");
        assertThat(q.isResolved()).isFalse();
        assertThat(q.getAcceptedAnswer()).isNull();
        assertThat(q.getInterestCount()).isZero();
        assertThat(q.getCreatedAt()).isNotNull();
        assertThat(q.getUpdatedAt()).isNotNull();
    }

    @Test
    void fullRelationalGraphRoundTrips() {
        User alice = users.save(new User("alice@smail.iitm.ac.in"));
        User bob = users.save(new User("bob@smail.iitm.ac.in"));
        Question q = questions.save(new Question(alice, "WiFi drops", "Disconnects often"));

        // multiple tags per question
        for (String name : List.of("Networking", "WiFi", "Hostel")) {
            questionTags.save(new QuestionTag(q, tags.save(new Tag(name))));
        }

        Answer answer = answers.save(new Answer(q, bob, "Restart the router"));
        QuestionComment c = comments.save(new QuestionComment(q, bob, null, "Try DNS"));
        QuestionComment reply = comments.save(new QuestionComment(q, alice, c, "Did not help"));
        votes.save(new QuestionCommentVote(c, alice, VoteType.UP));
        interests.save(new QuestionInterest(q, bob));
        notifications.save(new Notification(alice, "COMMENT_ON_QUESTION", q, c));

        q.setAcceptedAnswer(answer);
        q.setResolved(true);
        flushAndClear();

        Question loaded = questions.findById(q.getId()).orElseThrow();
        assertThat(loaded.getQuestionTags()).extracting(qt -> qt.getTag().getName())
                .containsExactlyInAnyOrder("Networking", "WiFi", "Hostel");
        assertThat(loaded.isResolved()).isTrue();
        assertThat(loaded.getAcceptedAnswer().getBody()).isEqualTo("Restart the router");
        assertThat(answers.findByQuestionId(q.getId())).hasSize(1);

        List<QuestionComment> thread = comments.findByQuestionIdOrderByCreatedAtAsc(q.getId());
        assertThat(thread).hasSize(2);
        assertThat(comments.findById(reply.getId()).orElseThrow().getParentComment().getId()).isEqualTo(c.getId());

        QuestionCommentVote vote = votes.findById(new QuestionCommentVoteId(c.getId(), alice.getId())).orElseThrow();
        assertThat(vote.getVoteType()).isEqualTo(VoteType.UP);
        assertThat(interests.existsById(new QuestionInterestId(q.getId(), bob.getId()))).isTrue();

        List<Notification> n = notifications.findByUserIdOrderByCreatedAtDesc(alice.getId());
        assertThat(n).hasSize(1);
        assertThat(n.get(0).getComment().getId()).isEqualTo(c.getId());
        assertThat(n.get(0).isRead()).isFalse();
    }

    @Test
    void tagCanBelongToManyQuestions() {
        User alice = users.save(new User("alice@smail.iitm.ac.in"));
        Tag wifi = tags.save(new Tag("WiFi"));
        Question q1 = questions.save(new Question(alice, "Q1", "b"));
        Question q2 = questions.save(new Question(alice, "Q2", "b"));
        questionTags.save(new QuestionTag(q1, wifi));
        questionTags.save(new QuestionTag(q2, wifi));
        flushAndClear();
        assertThat(questionTags.count()).isEqualTo(2);
    }

    @Test
    void databaseRejectsAcceptedAnswerFromAnotherQuestion() {
        User alice = users.save(new User("alice@smail.iitm.ac.in"));
        Question q1 = questions.save(new Question(alice, "Q1", "b"));
        Question q2 = questions.save(new Question(alice, "Q2", "b"));
        Answer onQ2 = answers.save(new Answer(q2, alice, "answer"));
        q1.setAcceptedAnswer(onQ2);
        assertThatThrownBy(() -> em.flush()).hasRootCauseInstanceOf(java.sql.SQLException.class);
    }

    @Test
    void duplicateSmailRejected() {
        users.save(new User("alice@smail.iitm.ac.in"));
        assertThatThrownBy(() -> users.saveAndFlush(new User("alice@smail.iitm.ac.in")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
