package com.fixit.entity;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class QuestionInterestId implements Serializable {

    @Column(name = "question_id")
    private Long questionId;

    @Column(name = "user_id")
    private Long userId;

    protected QuestionInterestId() {
    }

    public QuestionInterestId(Long questionId, Long userId) {
        this.questionId = questionId;
        this.userId = userId;
    }

    public Long getQuestionId() { return questionId; }
    public Long getUserId() { return userId; }

    @Override
    public boolean equals(Object o) {
        return o instanceof QuestionInterestId other
                && Objects.equals(questionId, other.questionId) && Objects.equals(userId, other.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(questionId, userId);
    }
}
