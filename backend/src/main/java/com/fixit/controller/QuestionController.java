package com.fixit.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import com.fixit.dto.AcceptAnswerRequest;
import com.fixit.dto.PageResponse;
import com.fixit.dto.QuestionRequest;
import com.fixit.dto.QuestionResponse;
import com.fixit.dto.TagListRequest;
import com.fixit.dto.TagResponse;
import com.fixit.security.FixItPrincipal;
import com.fixit.security.RateLimiter;
import com.fixit.dto.QuestionSummary;
import com.fixit.service.DiscoveryService;
import com.fixit.service.QuestionService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/questions")
public class QuestionController {

    private final QuestionService service;
    private final DiscoveryService discovery;
    private final RateLimiter rateLimiter;

    public QuestionController(QuestionService service, DiscoveryService discovery, RateLimiter rateLimiter) {
        this.service = service;
        this.discovery = discovery;
        this.rateLimiter = rateLimiter;
    }

    /**
     * Homepage discovery: GET /api/questions?tags=A&tags=B&page=0&size=20
     * Questions having ANY selected tag (none selected = all), most "+" first.
     */
    @GetMapping
    public PageResponse<QuestionSummary> discover(HttpServletRequest request,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return discovery.getQuestions(RequestParams.tags(request), page, size);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public QuestionResponse create(@AuthenticationPrincipal FixItPrincipal me, @Valid @RequestBody QuestionRequest request) {
        rateLimiter.check(RateLimiter.QUESTION_WRITE, String.valueOf(me.getUserId()));   // saving embeds the question (paid call)
        return service.create(me.getUserId(), request);
    }

    @GetMapping("/{id}")
    public QuestionResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @PutMapping("/{id}")
    public QuestionResponse update(@AuthenticationPrincipal FixItPrincipal me, @PathVariable Long id,
            @Valid @RequestBody QuestionRequest request) {
        rateLimiter.check(RateLimiter.QUESTION_WRITE, String.valueOf(me.getUserId()));
        return service.update(me.getUserId(), id, request);
    }

    /** Question author selects the accepted answer; the question becomes resolved. */
    @PutMapping("/{id}/accepted-answer")
    public QuestionResponse acceptAnswer(@AuthenticationPrincipal FixItPrincipal me, @PathVariable Long id,
            @Valid @RequestBody AcceptAnswerRequest request) {
        return service.acceptAnswer(me.getUserId(), id, request.answerId());
    }

    @GetMapping("/{id}/tags")
    public List<TagResponse> tags(@PathVariable Long id) {
        return service.getTags(id);
    }

    /** Replaces the question's tags with exactly the given list (owner only). */
    @PutMapping("/{id}/tags")
    public List<TagResponse> setTags(@AuthenticationPrincipal FixItPrincipal me, @PathVariable Long id,
            @Valid @RequestBody TagListRequest request) {
        return service.setTags(me.getUserId(), id, request.tags());
    }
}
