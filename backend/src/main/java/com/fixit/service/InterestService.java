package com.fixit.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fixit.dto.InterestResponse;
import com.fixit.entity.Question;
import com.fixit.entity.QuestionInterest;
import com.fixit.entity.QuestionInterestId;
import com.fixit.exception.NotFoundException;
import com.fixit.repository.QuestionInterestRepository;
import com.fixit.repository.QuestionRepository;
import com.fixit.repository.UserRepository;

/**
 * The "+" action. Toggle: adds the user's interest row and increments interest_count, or (if already
 * interested) removes the row and decrements. The question row is locked for the duration, so
 * concurrent toggles can't lose updates and interest_count always equals the number of rows.
 * Does not touch questions.updated_at.
 */
@Service
public class InterestService {

    private final QuestionRepository questions;
    private final QuestionInterestRepository interests;
    private final UserRepository users;

    public InterestService(QuestionRepository questions, QuestionInterestRepository interests, UserRepository users) {
        this.questions = questions;
        this.interests = interests;
        this.users = users;
    }

    @Transactional
    public InterestResponse toggle(Long userId, Long questionId) {
        Question question = questions.findByIdForUpdate(questionId)
                .orElseThrow(() -> new NotFoundException("Question not found"));
        var id = new QuestionInterestId(questionId, userId);
        boolean interested;
        if (interests.existsById(id)) {
            interests.deleteById(id);
            question.setInterestCount(Math.max(0, question.getInterestCount() - 1));
            interested = false;
        } else {
            var user = users.findById(userId).orElseThrow(() -> new NotFoundException("User not found"));
            interests.save(new QuestionInterest(question, user));
            question.setInterestCount(question.getInterestCount() + 1);
            interested = true;
        }
        return new InterestResponse(questionId, interested, question.getInterestCount());
    }

    @Transactional(readOnly = true)
    public InterestResponse status(Long userId, Long questionId) {
        Question question = questions.findById(questionId).orElseThrow(() -> new NotFoundException("Question not found"));
        return new InterestResponse(questionId, interests.existsById(new QuestionInterestId(questionId, userId)),
                question.getInterestCount());
    }
}
