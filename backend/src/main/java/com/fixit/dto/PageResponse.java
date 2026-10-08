package com.fixit.dto;

import java.util.List;

import org.springframework.data.domain.Page;

public record PageResponse<T>(List<T> items, int page, int size, long totalItems, int totalPages) {

    public static <T> PageResponse<T> of(Page<?> page, List<T> items) {
        return new PageResponse<>(items, page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
