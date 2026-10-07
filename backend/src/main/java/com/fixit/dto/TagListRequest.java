package com.fixit.dto;

import java.util.List;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TagListRequest(@NotNull @Size(min = 1, max = 10) List<@jakarta.validation.constraints.NotBlank @Size(max = 50) String> tags) {
}
