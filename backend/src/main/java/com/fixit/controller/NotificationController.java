package com.fixit.controller;

import java.util.List;
import java.util.Map;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import com.fixit.dto.NotificationResponse;
import com.fixit.security.FixItPrincipal;
import com.fixit.service.NotificationService;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    /** The current user's notifications, newest first (limit 1-100, default 50). */
    @GetMapping
    public List<NotificationResponse> list(@AuthenticationPrincipal FixItPrincipal me,
            @RequestParam(defaultValue = "false") boolean unreadOnly, @RequestParam(required = false) Integer limit) {
        return service.list(me.getUserId(), unreadOnly, limit);
    }

    /** For the bell badge. */
    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount(@AuthenticationPrincipal FixItPrincipal me) {
        return Map.of("unreadCount", service.unreadCount(me.getUserId()));
    }

    @PutMapping("/{id}/read")
    public NotificationResponse markRead(@AuthenticationPrincipal FixItPrincipal me, @PathVariable Long id) {
        return service.markRead(me.getUserId(), id);
    }
}
