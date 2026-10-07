package com.fixit.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** {@code tags}: null = leave tags unchanged (on update); a list replaces the question's tags. */
public record QuestionRequest(
        @NotBlank @Size(max = 255) String title,
        @NotBlank @Size(max = 20000) String body,
        @Size(max = 10) List<@NotBlank @Size(max = 50) String> tags) {
}
