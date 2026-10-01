package com.calyvora.common.dto;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/** A standard page envelope for list endpoints, so large collections aren't returned all at once. */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> map) {
        return new PageResponse<>(
                page.getContent().stream().map(map).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    /** Transform the entries, keeping the paging envelope — e.g. to redact fields per viewer. */
    public <R> PageResponse<R> map(Function<T, R> map) {
        return new PageResponse<>(content.stream().map(map).toList(),
                page, size, totalElements, totalPages);
    }
}
