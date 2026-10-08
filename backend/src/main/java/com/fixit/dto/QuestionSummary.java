package com.fixit.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.fixit.entity.Question;
import com.fixit.search.SearchResultAssembler;

/** Compact question for lists (homepage, profile): preview only, not the full body. */
public record QuestionSummary(
        Long questionId,
        Long authorId,
        String authorName,
        String title,
        String bodyPreview,
        List<String> tags,
        boolean isResolved,
        int interestCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    /** Must be called inside a transaction (reads the lazy tag collection). */
    public static QuestionSummary from(Question q) {
        List<String> tags = q.getQuestionTags().stream().map(qt -> qt.getTag().getName())
                .sorted(String.CASE_INSENSITIVE_ORDER).toList();
        return new QuestionSummary(q.getId(), q.getAuthor().getId(), q.getAuthor().getDisplayName(), q.getTitle(),
                SearchResultAssembler.preview(q.getBody()), tags, q.isResolved(), q.getInterestCount(),
                q.getCreatedAt(), q.getUpdatedAt());
    }
}
