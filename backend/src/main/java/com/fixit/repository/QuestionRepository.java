package com.fixit.repository;

import java.util.Collection;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.fixit.entity.Question;

import jakarta.persistence.LockModeType;

public interface QuestionRepository extends JpaRepository<Question, Long> {

    /** Row-locks the question so concurrent "+" toggles are serialised and interest_count stays exact. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select q from Question q where q.id = :id")
    Optional<Question> findByIdForUpdate(@Param("id") Long id);

    @Query("select q.id from Question q order by q.id")
    java.util.List<Long> findAllIds();

    /** Homepage order: most "+" first, then newest. */
    @Query("select q from Question q order by q.interestCount desc, q.createdAt desc, q.id desc")
    Page<Question> findAllRanked(Pageable pageable);

    /** Union of tags: a question with ANY of the (lower-case) tag names, listed once. */
    @Query(value = "select q from Question q where exists (select 1 from QuestionTag qt where qt.question = q "
            + "and lower(qt.tag.name) in :names) order by q.interestCount desc, q.createdAt desc, q.id desc",
            countQuery = "select count(q) from Question q where exists (select 1 from QuestionTag qt "
                    + "where qt.question = q and lower(qt.tag.name) in :names)")
    Page<Question> findByAnyTagRanked(@Param("names") Collection<String> names, Pageable pageable);

    Page<Question> findByAuthorIdOrderByCreatedAtDescIdDesc(Long authorId, Pageable pageable);
}
