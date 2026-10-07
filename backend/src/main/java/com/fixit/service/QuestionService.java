package com.fixit.service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fixit.dto.QuestionRequest;
import com.fixit.dto.QuestionResponse;
import com.fixit.dto.TagResponse;
import com.fixit.entity.Question;
import com.fixit.entity.QuestionTag;
import com.fixit.entity.Tag;
import com.fixit.exception.BadRequestException;
import com.fixit.exception.ForbiddenException;
import com.fixit.exception.NotFoundException;
import com.fixit.repository.AnswerRepository;
import com.fixit.repository.QuestionRepository;
import com.fixit.repository.QuestionTagRepository;
import com.fixit.repository.UserRepository;
import com.fixit.search.QuestionContentChanged;

/** Returns DTOs (not entities) because mapping needs an open transaction for lazy collections. */
@Service
public class QuestionService {

    private final QuestionRepository questions;
    private final UserRepository users;
    private final TagService tagService;
    private final TagCatalog tagCatalog;
    private final QuestionTagRepository questionTags;
    private final AnswerRepository answers;
    private final ApplicationEventPublisher events;

    public QuestionService(QuestionRepository questions, UserRepository users, TagService tagService, TagCatalog tagCatalog,
            QuestionTagRepository questionTags, AnswerRepository answers, ApplicationEventPublisher events) {
        this.events = events;
        this.questionTags = questionTags;
        this.answers = answers;
        this.questions = questions;
        this.users = users;
        this.tagService = tagService;
        this.tagCatalog = tagCatalog;
    }

    @Transactional
    public QuestionResponse create(Long authorId, QuestionRequest request) {
        var author = users.findById(authorId).orElseThrow(() -> new NotFoundException("User not found"));
        if (request.tags() == null || request.tags().isEmpty()) {
            throw new BadRequestException("Choose at least one tag: " + String.join(", ", tagCatalog.names()));
        }
        List<Tag> chosenTags = tagService.resolve(request.tags());       // unknown tag -> 400 before anything is saved
        Question question = questions.save(new Question(author, request.title().trim(), request.body().trim()));
        applyTags(question, chosenTags);
        events.publishEvent(new QuestionContentChanged(question.getId())); // embedded after commit
        return QuestionResponse.from(question);
    }

    @Transactional(readOnly = true)
    public QuestionResponse get(Long questionId) {
        return QuestionResponse.from(findOrThrow(questionId));
    }

    /** Only the author may edit title, body and tags. */
    @Transactional
    public QuestionResponse update(Long userId, Long questionId, QuestionRequest request) {
        Question question = findOwned(userId, questionId);
        String newTitle = request.title().trim();
        String newBody = request.body().trim();
        boolean textChanged = !question.getTitle().equals(newTitle) || !question.getBody().equals(newBody);
        question.setTitle(newTitle);
        question.setBody(newBody);
        if (textChanged) {
            events.publishEvent(new QuestionContentChanged(questionId)); // re-embedded after commit
        }
        if (request.tags() != null) {
            replaceTags(question, request.tags());
        }
        question.touch();
        return QuestionResponse.from(question);
    }

    /**
     * Question author marks one answer as the accepted one and the question becomes resolved.
     * Picking a different answer later switches the accepted answer. Does not bump updated_at
     * (that tracks edits to the question's content).
     */
    @Transactional
    public QuestionResponse acceptAnswer(Long userId, Long questionId, Long answerId) {
        Question question = findOwnedBy(userId, questionId, "Only the question author can select the accepted answer");
        var answer = answers.findById(answerId).orElseThrow(() -> new NotFoundException("Answer not found"));
        if (!answer.getQuestion().getId().equals(questionId)) {
            throw new BadRequestException("Answer does not belong to this question");
        }
        question.setAcceptedAnswer(answer);
        question.setResolved(true);
        return QuestionResponse.from(question);
    }

    @Transactional(readOnly = true)
    public List<TagResponse> getTags(Long questionId) {
        return findOrThrow(questionId).getQuestionTags().stream()
                .map(qt -> TagResponse.from(qt.getTag()))
                .sorted(java.util.Comparator.comparing(TagResponse::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @Transactional
    public List<TagResponse> setTags(Long userId, Long questionId, List<String> names) {
        Question question = findOwned(userId, questionId);
        replaceTags(question, names);
        question.touch();
        return getTags(questionId);
    }

    private void replaceTags(Question question, List<String> names) {
        if (names.isEmpty()) {
            throw new BadRequestException("A question needs at least one tag: " + String.join(", ", tagCatalog.names()));
        }
        applyTags(question, tagService.resolve(names));
    }

    /** Makes the question's tags exactly {@code wanted}: removes missing ones, adds new ones. */
    private void applyTags(Question question, List<Tag> wanted) {
        Set<Long> wantedIds = wanted.stream().map(Tag::getId).collect(Collectors.toSet());
        // Delete explicitly as well as removing from the set: orphanRemoval alone is not reliably
        // triggered when the question was created in the same transaction (collection not yet tracked).
        List<QuestionTag> removed = question.getQuestionTags().stream()
                .filter(qt -> !wantedIds.contains(qt.getTag().getId())).toList();
        question.getQuestionTags().removeAll(removed);
        questionTags.deleteAll(removed);
        Set<Long> existingIds = question.getQuestionTags().stream()
                .map(qt -> qt.getTag().getId()).collect(Collectors.toSet());
        for (Tag tag : wanted) {
            if (!existingIds.contains(tag.getId())) {
                question.getQuestionTags().add(new QuestionTag(question, tag));
            }
        }
    }

    private Question findOwned(Long userId, Long questionId) {
        return findOwnedBy(userId, questionId, "Only the question author can edit this question");
    }

    private Question findOwnedBy(Long userId, Long questionId, String deniedMessage) {
        Question question = findOrThrow(questionId);
        if (!question.getAuthor().getId().equals(userId)) {
            throw new ForbiddenException(deniedMessage);
        }
        return question;
    }

    private Question findOrThrow(Long questionId) {
        return questions.findById(questionId).orElseThrow(() -> new NotFoundException("Question not found"));
    }
}
