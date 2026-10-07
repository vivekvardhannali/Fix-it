package com.fixit.dto;

import com.fixit.entity.VoteType;

import jakarta.validation.constraints.NotNull;

public record VoteRequest(@NotNull VoteType voteType) {
}
