package com.fixit.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import com.fixit.dto.AnswerRequest;
import com.fixit.dto.AnswerResponse;
import com.fixit.security.FixItPrincipal;
import com.fixit.service.AnswerService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api")
public class AnswerController {

    private final AnswerService service;

    public AnswerController(AnswerService service) {
        this.service = service;
    }

    @PostMapping("/questions/{questionId}/answers")
    @ResponseStatus(HttpStatus.CREATED)
    public AnswerResponse add(@AuthenticationPrincipal FixItPrincipal me, @PathVariable Long questionId,
            @Valid @RequestBody AnswerRequest request) {
        return service.add(me.getUserId(), questionId, request);
    }

    @GetMapping("/questions/{questionId}/answers")
    public List<AnswerResponse> list(@PathVariable Long questionId) {
        return service.listForQuestion(questionId);
    }

    @PutMapping("/answers/{answerId}")
    public AnswerResponse update(@AuthenticationPrincipal FixItPrincipal me, @PathVariable Long answerId,
            @Valid @RequestBody AnswerRequest request) {
        return service.update(me.getUserId(), answerId, request);
    }
}
