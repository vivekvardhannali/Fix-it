package com.fixit.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.fixit.entity.QuestionComment;

public interface QuestionCommentRepository extends JpaRepository<QuestionComment, Long> {
    List<QuestionComment> findByQuestionIdOrderByCreatedAtAsc(Long questionId);

    List<QuestionComment> findByQuestionIdOrderByCreatedAtAscIdAsc(Long questionId);
}
