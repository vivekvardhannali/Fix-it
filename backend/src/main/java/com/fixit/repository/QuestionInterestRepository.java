package com.fixit.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.fixit.entity.QuestionInterest;
import com.fixit.entity.QuestionInterestId;

public interface QuestionInterestRepository extends JpaRepository<QuestionInterest, QuestionInterestId> {
}
