package com.fixit.exception;

import java.io.IOException;
import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletResponse;

/** Consistent JSON error body used by every API error. */
public record ApiError(Instant timestamp, int status, String error, String message) {

    public static ApiError of(HttpStatus status, String message) {
        return new ApiError(Instant.now(), status.value(), status.getReasonPhrase(), message);
    }

    /** For errors raised in the security filter chain, before controllers/advice run. */
    public static void write(HttpServletResponse response, ObjectMapper mapper, HttpStatus status, String message)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        mapper.writeValue(response.getWriter(), of(status, message));
    }
}
