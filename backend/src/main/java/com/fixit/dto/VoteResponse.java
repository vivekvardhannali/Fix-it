package com.fixit.dto;

import com.fixit.entity.VoteType;

public record VoteResponse(Long commentId, long upCount, long downCount, VoteType myVote) {
}
