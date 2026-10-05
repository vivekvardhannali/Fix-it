package com.fixit.entity;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class QuestionTagId implements Serializable {

    @Column(name = "question_id")
    private Long questionId;

    @Column(name = "tag_id")
    private Long tagId;

    protected QuestionTagId() {
    }

    public QuestionTagId(Long questionId, Long tagId) {
        this.questionId = questionId;
        this.tagId = tagId;
    }

    public Long getQuestionId() { return questionId; }
    public Long getTagId() { return tagId; }

    @Override
    public boolean equals(Object o) {
        return o instanceof QuestionTagId other
                && Objects.equals(questionId, other.questionId) && Objects.equals(tagId, other.tagId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(questionId, tagId);
    }
}
