package com.fixit.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** {@code identifier} is the username or the smail. */
public record LoginRequest(
        @NotBlank @Size(max = 255) String identifier,
        @NotBlank @Size(max = 200) String password) {

    @Override
    public String toString() {
        return "LoginRequest[identifier=" + identifier + ", password=<hidden>]";
    }
}
