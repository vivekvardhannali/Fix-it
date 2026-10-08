package com.fixit.search;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.fixit.entity.Question;
import com.fixit.repository.QuestionRepository;

/** Turns ranked ids into result rows, keeping the given order. Shared by semantic, lexical and hybrid search. */
@Component
public class SearchResultAssembler {

    static final int PREVIEW_LINES = 3;
    static final int PREVIEW_CHARS = 200;

    private final QuestionRepository questions;

    public SearchResultAssembler(QuestionRepository questions) {
        this.questions = questions;
    }

    @Transactional(readOnly = true)
    public List<SearchResult> assemble(List<ScoredQuestion> ranked) {
        Map<Long, Question> byId = questions.findAllById(ranked.stream().map(ScoredQuestion::questionId).toList())
                .stream().collect(Collectors.toMap(Question::getId, Function.identity()));
        List<SearchResult> results = new ArrayList<>();
        for (ScoredQuestion s : ranked) {
            Question q = byId.get(s.questionId());
            if (q == null) {
                continue;
            }
            List<String> tags = q.getQuestionTags().stream().map(qt -> qt.getTag().getName())
                    .sorted(String.CASE_INSENSITIVE_ORDER).toList();
            results.add(new SearchResult(q.getId(), q.getTitle(), preview(q.getBody()), tags, q.isResolved(),
                    q.getInterestCount(), s.score(), s.semanticScore(), s.lexicalScore()));
        }
        return results;
    }

    /** "A few lines": at most 3 non-empty lines and 200 characters. */
    public static String preview(String body) {
        String firstLines = body.lines().map(String::strip).filter(l -> !l.isEmpty()).limit(PREVIEW_LINES)
                .collect(Collectors.joining("\n"));
        return firstLines.length() <= PREVIEW_CHARS ? firstLines : firstLines.substring(0, PREVIEW_CHARS).stripTrailing() + "…";
    }
}
