package com.fixit.entity;

import java.time.LocalDateTime;

import jakarta.persistence.*;

@Entity
@Table(name = "question_interests")
public class QuestionInterest {

    @EmbeddedId
    private QuestionInterestId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("questionId")
    @JoinColumn(name = "question_id")
    private Question question;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("userId")
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected QuestionInterest() {
    }

    public QuestionInterest(Question question, User user) {
        this.id = new QuestionInterestId(question.getId(), user.getId());
        this.question = question;
        this.user = user;
    }

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public QuestionInterestId getId() { return id; }
    public Question getQuestion() { return question; }
    public User getUser() { return user; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
