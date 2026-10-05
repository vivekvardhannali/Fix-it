package com.fixit.entity;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

import jakarta.persistence.*;

@Entity
@Table(name = "questions")
public class Question {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "question_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String body;

    @Column(name = "is_resolved", nullable = false)
    private boolean resolved;

    // The DB uses a composite FK (accepted_answer_id, question_id) so the accepted answer
    // must belong to this question; JPA only needs the answer reference.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "accepted_answer_id")
    private Answer acceptedAnswer;

    @Column(name = "interest_count", nullable = false)
    private int interestCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "question", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<QuestionTag> questionTags = new LinkedHashSet<>();

    protected Question() {
    }

    public Question(User author, String title, String body) {
        this.author = author;
        this.title = title;
        this.body = body;
    }

    // created_at / updated_at are set here; updated_at is bumped explicitly by services via touch()
    @PrePersist
    void onCreate() {
        createdAt = updatedAt = LocalDateTime.now();
    }

    public void touch() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public User getAuthor() { return author; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public boolean isResolved() { return resolved; }
    public void setResolved(boolean resolved) { this.resolved = resolved; }
    public Answer getAcceptedAnswer() { return acceptedAnswer; }
    public void setAcceptedAnswer(Answer acceptedAnswer) { this.acceptedAnswer = acceptedAnswer; }
    public int getInterestCount() { return interestCount; }
    public void setInterestCount(int interestCount) { this.interestCount = interestCount; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public Set<QuestionTag> getQuestionTags() { return questionTags; }
}
