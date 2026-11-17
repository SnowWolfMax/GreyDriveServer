package com.greydrive.common;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Set;

public final class Paging {

    private Paging() {
    }

    public static Pageable of(Integer page, Integer size, String sort, Set<String> allowedSortFields,
                              String defaultSort, int defaultSize, int maxSize) {
        int p = page == null || page < 1 ? 1 : page;
        int s = size == null || size < 1 ? defaultSize : Math.min(size, maxSize);
        Sort sortSpec = parseSort(sort, allowedSortFields, defaultSort);
        return PageRequest.of(p - 1, s, sortSpec);
    }

    public static Sort parseSort(String sort, Set<String> allowed, String defaultSort) {
        String value = (sort == null || sort.isBlank()) ? defaultSort : sort;
        if (value == null || value.isBlank()) {
            return Sort.unsorted();
        }
        String[] parts = value.split(",");
        String field = parts[0].trim();
        if (!allowed.contains(field)) {
            field = defaultSort.split(",")[0].trim();
        }
        Sort.Direction direction = parts.length > 1 && "asc".equalsIgnoreCase(parts[1].trim())
                ? Sort.Direction.ASC : Sort.Direction.DESC;
        return Sort.by(direction, field);
    }
}
