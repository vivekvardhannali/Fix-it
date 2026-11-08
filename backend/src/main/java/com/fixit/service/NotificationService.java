package com.fixit.service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
 * - reply to a comment               -> the author of EVERY ancestor comment up to the top-level one (REPLY_TO_COMMENT),
 *                                       one notification per person even if they wrote several ancestors
 * - reply anywhere in a thread       -> ALSO the question's author (COMMENT_ON_QUESTION), unless they already get a
 *                                       REPLY_TO_COMMENT for the same reply as an ancestor
 * Never notify someone about their own action, and never more than one notification per person per comment.
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
        Long actorId = comment.getAuthor().getId();
        if (comment.getParentComment() == null) {
            var questionAuthor = comment.getQuestion().getAuthor();
            if (!questionAuthor.getId().equals(actorId)) {
                notifications.save(new Notification(questionAuthor, NotificationType.COMMENT_ON_QUESTION.name(),
                        comment.getQuestion(), comment));
            }
            return;
        }
        // A reply notifies every ancestor on the path up to the top-level comment: once per person, never the replier.
        Set<Long> alreadyNotified = new HashSet<>();
        alreadyNotified.add(actorId);
        for (QuestionComment ancestor = comment.getParentComment(); ancestor != null; ancestor = ancestor.getParentComment()) {
            var recipient = ancestor.getAuthor();
            if (alreadyNotified.add(recipient.getId())) {
                notifications.save(new Notification(recipient, NotificationType.REPLY_TO_COMMENT.name(),
                        comment.getQuestion(), comment));
            }
        }
        // The question author is told about every new comment in their thread, however deep (unless they were already
        // notified above as an ancestor, or wrote the reply themselves).
        var questionAuthor = comment.getQuestion().getAuthor();
        if (alreadyNotified.add(questionAuthor.getId())) {
            notifications.save(new Notification(questionAuthor, NotificationType.COMMENT_ON_QUESTION.name(),
                    comment.getQuestion(), comment));
        }
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
