package com.fixit.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.fixit.entity.VoteType;

/**
 * A comment with its nested replies. {@code myVote} is the requesting user's vote (null if none).
 * Votes are display-only: they never affect ordering, search or the question's "+" count.
 */
public record CommentResponse(
        Long commentId,
        Long questionId,
        Long authorId,
        String authorName,
        Long parentCommentId,
        String body,
        long upCount,
        long downCount,
        VoteType myVote,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<CommentResponse> replies) {
}
