package com.fixit.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fixit.dto.AnswerRequest;
import com.fixit.dto.AnswerResponse;
import com.fixit.entity.Answer;
import com.fixit.entity.Question;
import com.fixit.exception.ForbiddenException;
import com.fixit.exception.NotFoundException;
import com.fixit.repository.AnswerRepository;
import com.fixit.repository.QuestionRepository;
import com.fixit.repository.UserRepository;

@Service
public class AnswerService {

    private final AnswerRepository answers;
    private final QuestionRepository questions;
    private final UserRepository users;

    public AnswerService(AnswerRepository answers, QuestionRepository questions, UserRepository users) {
        this.answers = answers;
        this.questions = questions;
        this.users = users;
    }

    @Transactional
    public AnswerResponse add(Long userId, Long questionId, AnswerRequest request) {
        Question question = findQuestion(questionId);
        var author = users.findById(userId).orElseThrow(() -> new NotFoundException("User not found"));
        return AnswerResponse.from(answers.save(new Answer(question, author, request.body().trim())));
    }

    @Transactional(readOnly = true)
    public List<AnswerResponse> listForQuestion(Long questionId) {
        findQuestion(questionId);
        return answers.findByQuestionIdOrderByCreatedAtAscIdAsc(questionId).stream().map(AnswerResponse::from).toList();
    }

    /** Only the answer's author may edit it (not even the question's author). */
    @Transactional
    public AnswerResponse update(Long userId, Long answerId, AnswerRequest request) {
        Answer answer = answers.findById(answerId).orElseThrow(() -> new NotFoundException("Answer not found"));
        if (!answer.getAuthor().getId().equals(userId)) {
            throw new ForbiddenException("Only the answer author can edit this answer");
        }
        answer.setBody(request.body().trim());
        answer.touch();
        return AnswerResponse.from(answer);
    }

    private Question findQuestion(Long questionId) {
        return questions.findById(questionId).orElseThrow(() -> new NotFoundException("Question not found"));
    }
}
