package com.fixit.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import com.fixit.dto.InterestResponse;
import com.fixit.security.FixItPrincipal;
import com.fixit.service.InterestService;

@RestController
@RequestMapping("/api/questions/{questionId}/interest")
public class InterestController {

    private final InterestService service;

    public InterestController(InterestService service) {
        this.service = service;
    }

    /** The "+" button: toggles the current user's interest in the question. */
    @PostMapping
    public InterestResponse toggle(@AuthenticationPrincipal FixItPrincipal me, @PathVariable Long questionId) {
        return service.toggle(me.getUserId(), questionId);
    }

    @GetMapping
    public InterestResponse status(@AuthenticationPrincipal FixItPrincipal me, @PathVariable Long questionId) {
        return service.status(me.getUserId(), questionId);
    }
}
