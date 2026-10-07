package com.fixit.dto;

import java.time.LocalDateTime;

import com.fixit.entity.Answer;

public record AnswerResponse(
        Long answerId,
        Long questionId,
        Long authorId,
        String authorName,
        String body,
        boolean isAccepted,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static AnswerResponse from(Answer a) {
        var accepted = a.getQuestion().getAcceptedAnswer();
        return new AnswerResponse(a.getId(), a.getQuestion().getId(), a.getAuthor().getId(), a.getAuthor().getDisplayName(), a.getBody(),
                accepted != null && accepted.getId().equals(a.getId()), a.getCreatedAt(), a.getUpdatedAt());
    }
}
