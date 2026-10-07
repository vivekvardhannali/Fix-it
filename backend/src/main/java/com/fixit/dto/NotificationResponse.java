package com.fixit.dto;

import java.time.LocalDateTime;

/**
 * {@code commentId}/{@code actorId}/{@code actorName} identify the comment that triggered it and who wrote it;
 * {@code questionTitle} lets the bell show the text without another request.
 */
public record NotificationResponse(
        Long notificationId,
        String type,
        Long questionId,
        String questionTitle,
        Long commentId,
        Long actorId,
        String actorName,
        boolean isRead,
        LocalDateTime createdAt) {
}
