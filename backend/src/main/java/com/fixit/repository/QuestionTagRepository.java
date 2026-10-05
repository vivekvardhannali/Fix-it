package com.fixit.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.fixit.entity.QuestionTag;
import com.fixit.entity.QuestionTagId;

public interface QuestionTagRepository extends JpaRepository<QuestionTag, QuestionTagId> {
}
