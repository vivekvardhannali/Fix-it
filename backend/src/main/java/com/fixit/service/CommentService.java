package com.fixit.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fixit.dto.CommentRequest;
import com.fixit.dto.CommentResponse;
import com.fixit.entity.Question;
import com.fixit.entity.QuestionComment;
import com.fixit.entity.QuestionCommentVote;
import com.fixit.entity.QuestionCommentVoteId;
import com.fixit.entity.VoteType;
import com.fixit.exception.ForbiddenException;
import com.fixit.exception.NotFoundException;
import com.fixit.repository.QuestionCommentRepository;
import com.fixit.repository.QuestionCommentVoteRepository;
import com.fixit.repository.QuestionRepository;
import com.fixit.repository.UserRepository;

/** Comments belong to questions only (never answers); replies point at a parent comment. No delete. */
@Service
public class CommentService {

    private final QuestionCommentRepository comments;
    private final QuestionRepository questions;
    private final UserRepository users;
    private final QuestionCommentVoteRepository votes;
    private final NotificationService notifications;

    public CommentService(QuestionCommentRepository comments, QuestionRepository questions, UserRepository users,
            QuestionCommentVoteRepository votes, NotificationService notifications) {
        this.votes = votes;
        this.notifications = notifications;
        this.comments = comments;
        this.questions = questions;
        this.users = users;
    }

    @Transactional
    public CommentResponse addComment(Long userId, Long questionId, CommentRequest request) {
        Question question = questions.findById(questionId).orElseThrow(() -> new NotFoundException("Question not found"));
        return save(userId, question, null, request);
    }

    /** The reply always lands on the same question as its parent. */
    @Transactional
    public CommentResponse addReply(Long userId, Long parentCommentId, CommentRequest request) {
        QuestionComment parent = findComment(parentCommentId);
        return save(userId, parent.getQuestion(), parent, request);
    }

    /** Whole discussion as a tree: top-level comments oldest first, each with nested replies. */
    @Transactional(readOnly = true)
    public List<CommentResponse> thread(Long userId, Long questionId) {
        questions.findById(questionId).orElseThrow(() -> new NotFoundException("Question not found"));
        List<QuestionComment> all = comments.findByQuestionIdOrderByCreatedAtAscIdAsc(questionId);
        Map<Long, List<QuestionComment>> byParent = new HashMap<>();
        for (QuestionComment c : all) {
            Long parentId = c.getParentComment() == null ? null : c.getParentComment().getId();
            byParent.computeIfAbsent(parentId, k -> new ArrayList<>()).add(c);
        }
        VoteInfo info = voteInfo(userId, questionId);
        return children(byParent, null, info);
    }

    @Transactional
    public CommentResponse edit(Long userId, Long commentId, CommentRequest request) {
        QuestionComment comment = findComment(commentId);
        if (!comment.getAuthor().getId().equals(userId)) {
            throw new ForbiddenException("Only the comment author can edit this comment");
        }
        comment.setBody(request.body().trim());
        comment.touch();
        return toResponse(comment, List.of(), singleVoteInfo(userId, comment.getId()));
    }

    private CommentResponse save(Long userId, Question question, QuestionComment parent, CommentRequest request) {
        var author = users.findById(userId).orElseThrow(() -> new NotFoundException("User not found"));
        QuestionComment saved = comments.save(new QuestionComment(question, author, parent, request.body().trim()));
        notifications.onCommentCreated(saved);
        return toResponse(saved, List.of(), VoteInfo.NONE);
    }

    private List<CommentResponse> children(Map<Long, List<QuestionComment>> byParent, Long parentId, VoteInfo info) {
        return byParent.getOrDefault(parentId, List.of()).stream()
                .map(c -> toResponse(c, children(byParent, c.getId(), info), info))
                .toList();
    }

    private static CommentResponse toResponse(QuestionComment c, List<CommentResponse> replies, VoteInfo info) {
        return new CommentResponse(c.getId(), c.getQuestion().getId(), c.getAuthor().getId(), c.getAuthor().getDisplayName(),
                c.getParentComment() == null ? null : c.getParentComment().getId(),
                c.getBody(), info.up(c.getId()), info.down(c.getId()), info.mine(c.getId()),
                c.getCreatedAt(), c.getUpdatedAt(), replies);
    }

    /** Vote counts per comment plus the requesting user's own votes, loaded with two queries. */
    private record VoteInfo(Map<Long, Long> up, Map<Long, Long> down, Map<Long, VoteType> mine) {
        static final VoteInfo NONE = new VoteInfo(Map.of(), Map.of(), Map.of());

        long up(Long commentId) { return up.getOrDefault(commentId, 0L); }
        long down(Long commentId) { return down.getOrDefault(commentId, 0L); }
        VoteType mine(Long commentId) { return mine.get(commentId); }
    }

    private VoteInfo voteInfo(Long userId, Long questionId) {
        Map<Long, Long> up = new HashMap<>();
        Map<Long, Long> down = new HashMap<>();
        for (Object[] row : votes.countsForQuestion(questionId)) {
            ((VoteType) row[1] == VoteType.UP ? up : down).put((Long) row[0], (Long) row[2]);
        }
        Map<Long, VoteType> mine = new HashMap<>();
        for (QuestionCommentVote v : votes.findByUserAndQuestion(userId, questionId)) {
            mine.put(v.getId().getCommentId(), v.getVoteType());
        }
        return new VoteInfo(up, down, mine);
    }

    private VoteInfo singleVoteInfo(Long userId, Long commentId) {
        Map<Long, VoteType> mine = new HashMap<>();
        votes.findById(new QuestionCommentVoteId(commentId, userId)).ifPresent(v -> mine.put(commentId, v.getVoteType()));
        return new VoteInfo(
                Map.of(commentId, votes.countByIdCommentIdAndVoteType(commentId, VoteType.UP)),
                Map.of(commentId, votes.countByIdCommentIdAndVoteType(commentId, VoteType.DOWN)), mine);
    }

    private QuestionComment findComment(Long commentId) {
        return comments.findById(commentId).orElseThrow(() -> new NotFoundException("Comment not found"));
    }
}
