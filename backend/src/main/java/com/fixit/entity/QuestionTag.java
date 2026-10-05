package com.fixit.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "question_tags")
public class QuestionTag {

    @EmbeddedId
    private QuestionTagId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("questionId")
    @JoinColumn(name = "question_id")
    private Question question;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("tagId")
    @JoinColumn(name = "tag_id")
    private Tag tag;

    protected QuestionTag() {
    }

    public QuestionTag(Question question, Tag tag) {
        this.id = new QuestionTagId(question.getId(), tag.getId());
        this.question = question;
        this.tag = tag;
    }

    public QuestionTagId getId() { return id; }
    public Question getQuestion() { return question; }
    public Tag getTag() { return tag; }

    @Override
    public boolean equals(Object o) {
        return o instanceof QuestionTag other && java.util.Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hashCode(id);
    }
}
