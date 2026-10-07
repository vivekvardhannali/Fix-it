package com.fixit.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import com.fixit.dto.VoteRequest;
import com.fixit.dto.VoteResponse;
import com.fixit.security.FixItPrincipal;
import com.fixit.service.CommentVoteService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/comments/{commentId}/vote")
public class VoteController {

    private final CommentVoteService service;

    public VoteController(CommentVoteService service) {
        this.service = service;
    }

    /** Click semantics: UP/DOWN when no vote; same again removes; opposite switches. */
    @PutMapping
    public VoteResponse vote(@AuthenticationPrincipal FixItPrincipal me, @PathVariable Long commentId,
            @Valid @RequestBody VoteRequest request) {
        return service.vote(me.getUserId(), commentId, request.voteType());
    }

    @DeleteMapping
    public VoteResponse remove(@AuthenticationPrincipal FixItPrincipal me, @PathVariable Long commentId) {
        return service.remove(me.getUserId(), commentId);
    }
}
