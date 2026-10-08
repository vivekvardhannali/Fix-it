package com.fixit.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fixit.security.FixItPrincipal;
import com.fixit.security.RateLimiter;
import com.fixit.search.SearchMode;
import com.fixit.search.SearchResult;
import com.fixit.search.SearchService;

import jakarta.servlet.http.HttpServletRequest;

@RestController
public class SearchController {

    private final SearchService service;
    private final RateLimiter rateLimiter;

    public SearchController(SearchService service, RateLimiter rateLimiter) {
        this.service = service;
        this.rateLimiter = rateLimiter;
    }

    /**
     * GET /api/search?q=wifi+keeps+dropping&tags=Networking&tags=Hostel[&mode=hybrid|semantic|lexical]
     * Tags are optional (none = search everything; several = ANY of them). Results are already thresholded and
     * sorted by decreasing relevance. {@code mode} defaults to hybrid; the others exist for tuning/comparison.
     */
    @GetMapping("/api/search")
    public List<SearchResult> search(@AuthenticationPrincipal FixItPrincipal me, @RequestParam("q") String query,
            @RequestParam(required = false) String mode, HttpServletRequest request) {
        rateLimiter.check(RateLimiter.SEARCH, String.valueOf(me.getUserId()));   // each search is one paid embedding request
        return service.searchQuestions(SearchMode.parse(mode), query, RequestParams.tags(request));
    }
}
