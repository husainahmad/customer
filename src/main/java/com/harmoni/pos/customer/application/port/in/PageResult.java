package com.harmoni.pos.customer.application.port.in;

import java.util.List;

/**
 * Paginated result container returned by portal use cases, with derived page count.
 */
public record PageResult<T>(List<T> content, long totalElements, int page, int size) {

    public PageResult {
        content = content == null ? List.of() : List.copyOf(content);
    }

    public int totalPages() {
        if (size <= 0) {
            return 0;
        }
        return (int) Math.ceil((double) totalElements / size);
    }
}
