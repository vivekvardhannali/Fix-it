package com.fixit.search;

/** Published when a question is created or its title/body changes; triggers re-embedding after commit. */
public record QuestionContentChanged(Long questionId) {
}
