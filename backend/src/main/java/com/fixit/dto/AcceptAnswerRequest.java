package com.fixit.dto;

import jakarta.validation.constraints.NotNull;

public record AcceptAnswerRequest(@NotNull Long answerId) {
}
