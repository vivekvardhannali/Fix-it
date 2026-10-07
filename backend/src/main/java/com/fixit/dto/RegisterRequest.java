package com.fixit.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Size(max = 30) String username,
        @NotBlank @Size(max = 255) String smail,
        @NotBlank @Size(max = 200) String password) {

    /** Never print the password. */
    @Override
    public String toString() {
        return "RegisterRequest[username=" + username + ", smail=" + smail + ", password=<hidden>]";
    }
}
