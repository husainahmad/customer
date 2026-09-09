package com.harmoni.pos.customer.adapter.in.web.dto;

import com.harmoni.pos.customer.application.port.in.PageResult;

import java.util.List;
import java.util.function.Function;

/**
 * Generic paginated response DTO wrapping content, totals and page metadata.
 */
public record PageResponse<T>(
        List<T> content,
        long totalElements,
        int totalPages,
        int page,
        int size
) {

    public static <D, R> PageResponse<R> of(PageResult<D> result, Function<D, R> mapper) {
        return new PageResponse<>(
                result.content().stream().map(mapper).toList(),
                result.totalElements(),
                result.totalPages(),
                result.page(),
                result.size());
    }
}
