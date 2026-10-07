package com.fixit.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fixit.dto.VoteResponse;
import com.fixit.entity.QuestionComment;
import com.fixit.entity.QuestionCommentVote;
import com.fixit.entity.QuestionCommentVoteId;
import com.fixit.entity.VoteType;
import com.fixit.exception.NotFoundException;
import com.fixit.repository.QuestionCommentRepository;
import com.fixit.repository.QuestionCommentVoteRepository;
import com.fixit.repository.UserRepository;

/**
 * Click semantics (one active vote per user per comment):
 * none -> UP/DOWN creates; same type again removes; the other type switches.
 * Touches only question_comment_votes - never interest_count, ordering or anything search related.
 */
@Service
public class CommentVoteService {

    private final QuestionCommentVoteRepository votes;
    private final QuestionCommentRepository comments;
    private final UserRepository users;

    public CommentVoteService(QuestionCommentVoteRepository votes, QuestionCommentRepository comments,
            UserRepository users) {
        this.votes = votes;
        this.comments = comments;
        this.users = users;
    }

    @Transactional
    public VoteResponse vote(Long userId, Long commentId, VoteType clicked) {
        QuestionComment comment = findComment(commentId);
        var existing = votes.findById(new QuestionCommentVoteId(commentId, userId));
        VoteType result;
        if (existing.isEmpty()) {
            var user = users.findById(userId).orElseThrow(() -> new NotFoundException("User not found"));
            votes.save(new QuestionCommentVote(comment, user, clicked));
            result = clicked;
        } else if (existing.get().getVoteType() == clicked) {
            votes.delete(existing.get());
            result = null;
        } else {
            existing.get().changeVote(clicked);
            result = clicked;
        }
        return summary(commentId, result);
    }

    /** Explicit removal; harmless if the user has no vote. */
    @Transactional
    public VoteResponse remove(Long userId, Long commentId) {
        findComment(commentId);
        votes.findById(new QuestionCommentVoteId(commentId, userId)).ifPresent(votes::delete);
        return summary(commentId, null);
    }

    private VoteResponse summary(Long commentId, VoteType mine) {
        // the count queries flush pending inserts/deletes/updates first
        return new VoteResponse(commentId, votes.countByIdCommentIdAndVoteType(commentId, VoteType.UP),
                votes.countByIdCommentIdAndVoteType(commentId, VoteType.DOWN), mine);
    }

    private QuestionComment findComment(Long commentId) {
        return comments.findById(commentId).orElseThrow(() -> new NotFoundException("Comment not found"));
    }
}
