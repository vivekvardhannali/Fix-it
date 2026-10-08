package com.fixit.service;

import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fixit.dto.PageResponse;
import com.fixit.dto.QuestionSummary;
import com.fixit.exception.BadRequestException;
import com.fixit.repository.QuestionRepository;
import com.fixit.search.SearchInput;

/**
 * Homepage discovery: union of the selected tags (a question matching ANY selected tag appears once), sorted by the
 * number of "+" users, highest first; ties: newest first. No tags selected = all questions. This is NOT search
 * ranking: it ignores relevance and uses only the "+" count.
 */
@Service
public class DiscoveryService {

    public static final int MAX_PAGE_SIZE = 50;
    public static final int DEFAULT_PAGE_SIZE = 20;

    private final QuestionRepository questions;
    private final TagCatalog catalog;

    public DiscoveryService(QuestionRepository questions, TagCatalog catalog) {
        this.questions = questions;
        this.catalog = catalog;
    }

    @Transactional(readOnly = true)
    public PageResponse<QuestionSummary> getQuestions(List<String> tagNames, int page, int size) {
        var pageable = pageable(page, size);
        List<String> tags = tagNames == null || tagNames.isEmpty() ? List.of() : SearchInput.tags(catalog.requireKnown(tagNames));
        var found = tags.isEmpty() ? questions.findAllRanked(pageable) : questions.findByAnyTagRanked(tags, pageable);
        return PageResponse.of(found, found.getContent().stream().map(QuestionSummary::from).toList());
    }

    /** The logged-in user's own questions, newest first (profile page). */
    @Transactional(readOnly = true)
    public PageResponse<QuestionSummary> getMyQuestions(Long userId, int page, int size) {
        var found = questions.findByAuthorIdOrderByCreatedAtDescIdDesc(userId, pageable(page, size));
        return PageResponse.of(found, found.getContent().stream().map(QuestionSummary::from).toList());
    }

    private static PageRequest pageable(int page, int size) {
        if (page < 0) {
            throw new BadRequestException("page must be 0 or more");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new BadRequestException("size must be between 1 and " + MAX_PAGE_SIZE);
        }
        return PageRequest.of(page, size);
    }
}
