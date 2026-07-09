package com.archi.ordermanagement.core.domain.paging;

/** Domain-level pagination request, decoupled from any persistence framework type. */
public record PageRequest(int page, int size) {

    public PageRequest {
        if (page < 0) {
            throw new IllegalArgumentException("page must be >= 0");
        }
        if (size <= 0) {
            throw new IllegalArgumentException("size must be > 0");
        }
    }
}
