package com.archi.ordermanagement.api.rest.dto;

import java.util.List;

public record PageResponse<T>(List<T> content, int page, int size, long totalElements) {
}
