package com.fixit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import com.fixit.entity.Question;
import com.fixit.entity.User;
import com.fixit.repository.QuestionRepository;
import com.fixit.repository.UserRepository;
import com.fixit.service.InterestService;

/**
 * NOT @Transactional on purpose: runs real concurrent transactions, which the rolled-back tests cannot.
 * Cleans up after itself; if it ever fails midway, check `select count(*) from users` and clean by hand.
 */
@SpringBootTest
class InterestConcurrencyTest {

    @Autowired InterestService service;
    @Autowired UserRepository users;
    @Autowired QuestionRepository questions;
    @Autowired JdbcTemplate jdbc;

    private final List<Long> userIds = new ArrayList<>();
    private Long questionId;

    @AfterEach
    void cleanUp() {
        if (questionId != null) {
            jdbc.update("delete from question_interests where question_id = ?", questionId);
            jdbc.update("delete from questions where question_id = ?", questionId);
        }
        userIds.forEach(id -> jdbc.update("delete from users where user_id = ?", id));
    }

    @Test
    void concurrentPlusFromManyUsers_countStaysExact() throws Exception {
        User author = users.save(new User("conc-author@smail.iitm.ac.in"));
        userIds.add(author.getId());
        questionId = questions.save(new Question(author, "t", "b")).getId();
        int n = 12;
        List<Long> voters = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            Long id = users.save(new User("conc-" + i + "@smail.iitm.ac.in")).getId();
            userIds.add(id);
            voters.add(id);
        }

        runConcurrently(voters.stream().map(id -> (Callable<Object>) () -> service.toggle(id, questionId)).toList());

        assertThat(counter()).isEqualTo(n);
        assertThat(rows()).isEqualTo(n);
    }

    @Test
    void sameUserDoubleClickRace_neverDuplicatesAndStaysConsistent() throws Exception {
        User author = users.save(new User("conc-author2@smail.iitm.ac.in"));
        userIds.add(author.getId());
        questionId = questions.save(new Question(author, "t", "b")).getId();

        Long uid = author.getId();
        runConcurrently(List.of(() -> service.toggle(uid, questionId), () -> service.toggle(uid, questionId),
                () -> service.toggle(uid, questionId)));

        // 3 toggles in any order -> interested, one row, counter agrees
        assertThat(rows()).isEqualTo(1);
        assertThat(counter()).isEqualTo(1);
    }

    private int counter() {
        return jdbc.queryForObject("select interest_count from questions where question_id = ?", Integer.class, questionId);
    }

    private int rows() {
        return jdbc.queryForObject("select count(*) from question_interests where question_id = ?", Integer.class, questionId);
    }

    private static void runConcurrently(List<Callable<Object>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Object>> futures = new ArrayList<>();
        for (Callable<Object> t : tasks) {
            futures.add(pool.submit(() -> {
                start.await();
                return t.call();
            }));
        }
        start.countDown();
        for (Future<Object> f : futures) {
            f.get();
        }
        pool.shutdown();
    }
}
