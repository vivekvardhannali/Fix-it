package com.fixit.controller;

import java.util.Arrays;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

/** Helpers for reading request parameters. */
final class RequestParams {

    private RequestParams() {
    }

    /**
     * Repeated parameter (?tags=a&tags=b). Read raw on purpose: Spring's List binding would split a single value on
     * commas, which would break tag names that contain a comma.
     */
    static List<String> tags(HttpServletRequest request) {
        String[] values = request.getParameterValues("tags");
        return values == null ? List.of() : Arrays.asList(values);
    }
}
