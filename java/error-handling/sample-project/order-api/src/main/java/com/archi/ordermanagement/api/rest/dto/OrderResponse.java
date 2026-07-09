package com.archi.ordermanagement.api.rest.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OrderResponse(UUID id, String customerId, BigDecimal amount, String status, Instant createdAt) {
}
