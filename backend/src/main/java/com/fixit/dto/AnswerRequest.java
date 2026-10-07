package com.fixit.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AnswerRequest(@NotBlank @Size(max = 20000) String body) {
}
