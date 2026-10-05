package com.fixit.entity;

import java.time.LocalDateTime;

import jakarta.persistence.*;

@Entity
@Table(name = "question_comment_votes")
public class QuestionCommentVote {

    @EmbeddedId
    private QuestionCommentVoteId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("commentId")
    @JoinColumn(name = "comment_id")
    private QuestionComment comment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("userId")
    @JoinColumn(name = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "vote_type", nullable = false, length = 4)
    private VoteType voteType;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected QuestionCommentVote() {
    }

    public QuestionCommentVote(QuestionComment comment, User user, VoteType voteType) {
        this.id = new QuestionCommentVoteId(comment.getId(), user.getId());
        this.comment = comment;
        this.user = user;
        this.voteType = voteType;
    }

    @PrePersist
    void onCreate() {
        createdAt = updatedAt = LocalDateTime.now();
    }

    public void changeVote(VoteType voteType) {
        this.voteType = voteType;
        this.updatedAt = LocalDateTime.now();
    }

    public QuestionCommentVoteId getId() { return id; }
    public QuestionComment getComment() { return comment; }
    public User getUser() { return user; }
    public VoteType getVoteType() { return voteType; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
