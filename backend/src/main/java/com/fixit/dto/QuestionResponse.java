package com.fixit.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.fixit.entity.Question;

public record QuestionResponse(
        Long questionId,
        Long authorId,
        String authorName,
        String title,
        String body,
        List<String> tags,
        boolean isResolved,
        Long acceptedAnswerId,
        int interestCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    /** Must be called inside a transaction (reads the lazy tag collection). */
    public static QuestionResponse from(Question q) {
        List<String> tags = q.getQuestionTags().stream()
                .map(qt -> qt.getTag().getName())
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
        return new QuestionResponse(q.getId(), q.getAuthor().getId(), q.getAuthor().getDisplayName(), q.getTitle(), q.getBody(), tags,
                q.isResolved(), q.getAcceptedAnswer() == null ? null : q.getAcceptedAnswer().getId(),
                q.getInterestCount(), q.getCreatedAt(), q.getUpdatedAt());
    }
}
