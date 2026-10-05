package com.fixit.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.fixit.entity.QuestionCommentVote;
import com.fixit.entity.QuestionCommentVoteId;
import com.fixit.entity.VoteType;

public interface QuestionCommentVoteRepository extends JpaRepository<QuestionCommentVote, QuestionCommentVoteId> {

    long countByIdCommentIdAndVoteType(Long commentId, VoteType voteType);

    /** Rows of [commentId, voteType, count] for every voted comment of a question. */
    @Query("select v.id.commentId, v.voteType, count(v) from QuestionCommentVote v "
            + "where v.comment.question.id = :questionId group by v.id.commentId, v.voteType")
    List<Object[]> countsForQuestion(@Param("questionId") Long questionId);

    @Query("select v from QuestionCommentVote v where v.id.userId = :userId and v.comment.question.id = :questionId")
    List<QuestionCommentVote> findByUserAndQuestion(@Param("userId") Long userId, @Param("questionId") Long questionId);
}
