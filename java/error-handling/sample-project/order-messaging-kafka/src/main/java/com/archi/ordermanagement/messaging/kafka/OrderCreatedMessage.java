package com.archi.ordermanagement.messaging.kafka;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Wire message schema, deliberately distinct from the domain Order to decouple the Kafka contract. */
public record OrderCreatedMessage(UUID orderId, String customerId, BigDecimal amount, Instant occurredAt) {
}
