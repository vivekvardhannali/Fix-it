package com.fixit.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fixit.dto.MeResponse;
import com.fixit.dto.PageResponse;
import com.fixit.dto.QuestionSummary;
import com.fixit.entity.User;
import com.fixit.exception.NotFoundException;
import com.fixit.repository.UserRepository;
import com.fixit.security.FixItPrincipal;
import com.fixit.security.TabSessionService;
import com.fixit.service.DiscoveryService;

@RestController
public class MeController {

    private final UserRepository users;
    private final DiscoveryService discovery;
    private final TabSessionService tabSessions;     // TESTING-CONVENIENCE (D23)

    public MeController(UserRepository users, DiscoveryService discovery, TabSessionService tabSessions) {
        this.users = users;
        this.discovery = discovery;
        this.tabSessions = tabSessions;
    }

    @GetMapping("/api/me")
    public MeResponse me(@AuthenticationPrincipal FixItPrincipal principal) {
        User user = users.findById(principal.getUserId()).orElseThrow(() -> new NotFoundException("User not found"));
        return new MeResponse(user.getId(), user.getUsername(), user.getSmail(), tabSessions.enabled());
    }

    /** Profile page: the logged-in user's own questions, newest first. */
    @GetMapping("/api/me/questions")
    public PageResponse<QuestionSummary> myQuestions(@AuthenticationPrincipal FixItPrincipal principal,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return discovery.getMyQuestions(principal.getUserId(), page, size);
    }
}
