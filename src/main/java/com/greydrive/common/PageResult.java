package com.greydrive.common;

import org.springframework.data.domain.Page;

import java.util.List;

public record PageResult<T>(List<T> items, int page, int size, long total, int totalPages) {

    public static <T> PageResult<T> of(Page<T> page) {
        return new PageResult<>(page.getContent(), page.getNumber() + 1, page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }

    public static <T> PageResult<T> of(List<T> items, int page, int size, long total) {
        int totalPages = size <= 0 ? 0 : (int) ((total + size - 1) / size);
        return new PageResult<>(items, page, size, total, totalPages);
    }
}
