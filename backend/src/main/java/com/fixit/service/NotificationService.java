package com.fixit.service;

import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fixit.dto.NotificationResponse;
import com.fixit.entity.Notification;
import com.fixit.entity.NotificationType;
import com.fixit.entity.QuestionComment;
import com.fixit.exception.ForbiddenException;
import com.fixit.exception.NotFoundException;
import com.fixit.repository.NotificationRepository;

/**
 * Notification rules (only interactions defined in the design):
 * - top-level comment on a question  -> the question's author (COMMENT_ON_QUESTION)
 * - reply to a comment               -> the parent comment's author (REPLY_TO_COMMENT)
 * Never notify someone about their own action. A reply does not also notify the question author.
 */
@Service
public class NotificationService {

    static final int DEFAULT_LIMIT = 50;
    static final int MAX_LIMIT = 100;

    private final NotificationRepository notifications;

    public NotificationService(NotificationRepository notifications) {
        this.notifications = notifications;
    }

    /** Called from the same transaction that saved the comment, so both succeed or fail together. */
    @Transactional
    public void onCommentCreated(QuestionComment comment) {
        boolean isReply = comment.getParentComment() != null;
        var recipient = isReply ? comment.getParentComment().getAuthor() : comment.getQuestion().getAuthor();
        if (recipient.getId().equals(comment.getAuthor().getId())) {
            return;
        }
        NotificationType type = isReply ? NotificationType.REPLY_TO_COMMENT : NotificationType.COMMENT_ON_QUESTION;
        notifications.save(new Notification(recipient, type.name(), comment.getQuestion(), comment));
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> list(Long userId, boolean unreadOnly, Integer limit) {
        var page = PageRequest.of(0, clamp(limit));
        List<Notification> found = unreadOnly
                ? notifications.findByUserIdAndReadFalseOrderByCreatedAtDescIdDesc(userId, page)
                : notifications.findByUserIdOrderByCreatedAtDescIdDesc(userId, page);
        return found.stream().map(NotificationService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public long unreadCount(Long userId) {
        return notifications.countByUserIdAndReadFalse(userId);
    }

    /** Only the recipient may mark it read. Idempotent. */
    @Transactional
    public NotificationResponse markRead(Long userId, Long notificationId) {
        Notification n = notifications.findById(notificationId)
                .orElseThrow(() -> new NotFoundException("Notification not found"));
        if (!n.getUser().getId().equals(userId)) {
            throw new ForbiddenException("This notification belongs to another user");
        }
        n.setRead(true);
        return toResponse(n);
    }

    private static int clamp(Integer limit) {
        return limit == null ? DEFAULT_LIMIT : Math.max(1, Math.min(limit, MAX_LIMIT));
    }

    private static NotificationResponse toResponse(Notification n) {
        var comment = n.getComment();
        return new NotificationResponse(n.getId(), n.getType(), n.getQuestion().getId(), n.getQuestion().getTitle(),
                comment == null ? null : comment.getId(),
                comment == null ? null : comment.getAuthor().getId(),
                comment == null ? null : comment.getAuthor().getDisplayName(),
                n.isRead(), n.getCreatedAt());
    }
}
