package com.archi.ordermanagement.core.domain.paging;

import java.util.List;

/** Domain-level pagination result. {@code content} is guaranteed to never be null. */
public record PageResult<T>(List<T> content, int page, int size, long totalElements) {

    public PageResult {
        content = content == null ? List.of() : List.copyOf(content);
    }

    public static <T> PageResult<T> of(List<T> content, int page, int size, long totalElements) {
        return new PageResult<>(content, page, size, totalElements);
    }
}
