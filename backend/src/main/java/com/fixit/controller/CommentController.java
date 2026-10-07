package com.fixit.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import com.fixit.dto.CommentRequest;
import com.fixit.dto.CommentResponse;
import com.fixit.security.FixItPrincipal;
import com.fixit.service.CommentService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api")
public class CommentController {

    private final CommentService service;

    public CommentController(CommentService service) {
        this.service = service;
    }

    @PostMapping("/questions/{questionId}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public CommentResponse add(@AuthenticationPrincipal FixItPrincipal me, @PathVariable Long questionId,
            @Valid @RequestBody CommentRequest request) {
        return service.addComment(me.getUserId(), questionId, request);
    }

    @PostMapping("/comments/{commentId}/replies")
    @ResponseStatus(HttpStatus.CREATED)
    public CommentResponse reply(@AuthenticationPrincipal FixItPrincipal me, @PathVariable Long commentId,
            @Valid @RequestBody CommentRequest request) {
        return service.addReply(me.getUserId(), commentId, request);
    }

    @GetMapping("/questions/{questionId}/comments")
    public List<CommentResponse> thread(@AuthenticationPrincipal FixItPrincipal me, @PathVariable Long questionId) {
        return service.thread(me.getUserId(), questionId);
    }

    @PutMapping("/comments/{commentId}")
    public CommentResponse edit(@AuthenticationPrincipal FixItPrincipal me, @PathVariable Long commentId,
            @Valid @RequestBody CommentRequest request) {
        return service.edit(me.getUserId(), commentId, request);
    }
}
