package com.fixit.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.fixit.entity.Answer;

public interface AnswerRepository extends JpaRepository<Answer, Long> {
    List<Answer> findByQuestionId(Long questionId);

    List<Answer> findByQuestionIdOrderByCreatedAtAscIdAsc(Long questionId);
}
