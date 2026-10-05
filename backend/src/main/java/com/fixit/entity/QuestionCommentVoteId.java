package com.fixit.entity;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class QuestionCommentVoteId implements Serializable {

    @Column(name = "comment_id")
    private Long commentId;

    @Column(name = "user_id")
    private Long userId;

    protected QuestionCommentVoteId() {
    }

    public QuestionCommentVoteId(Long commentId, Long userId) {
        this.commentId = commentId;
        this.userId = userId;
    }

    public Long getCommentId() { return commentId; }
    public Long getUserId() { return userId; }

    @Override
    public boolean equals(Object o) {
        return o instanceof QuestionCommentVoteId other
                && Objects.equals(commentId, other.commentId) && Objects.equals(userId, other.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(commentId, userId);
    }
}
