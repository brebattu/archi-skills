package com.archi.ordermanagement.core.domain;

import com.archi.errorhandling.FunctionalException;
import com.archi.ordermanagement.core.domain.error.OrderErrorCode;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Order aggregate root. Owns its identity and enforces its own invariants; the identifier is
 * assigned by the domain (not by the persistence layer).
 */
public final class Order {

    private final UUID id;
    private final String customerId;
    private final BigDecimal amount;
    private final OrderStatus status;
    private final Instant createdAt;

    private Order(UUID id, String customerId, BigDecimal amount, OrderStatus status, Instant createdAt) {
        this.id = id;
        this.customerId = customerId;
        this.amount = amount;
        this.status = status;
        this.createdAt = createdAt;
    }

    public static Order create(String customerId, BigDecimal amount) {
        if (customerId == null || customerId.isBlank()) {
            throw new FunctionalException(OrderErrorCode.INVALID_CUSTOMER_ID, "customerId must not be blank");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new FunctionalException(OrderErrorCode.INVALID_ORDER_AMOUNT, "amount must be positive");
        }
        return new Order(UUID.randomUUID(), customerId, amount, OrderStatus.CREATED, Instant.now());
    }

    /** Rebuilds an already-valid Order from persisted state, bypassing creation invariants. */
    public static Order reconstruct(UUID id, String customerId, BigDecimal amount, OrderStatus status,
                                     Instant createdAt) {
        return new Order(id, customerId, amount, status, createdAt);
    }

    public Order cancel() {
        if (status == OrderStatus.CANCELLED) {
            throw new FunctionalException(OrderErrorCode.ORDER_ALREADY_CANCELLED,
                    "Order " + id + " is already cancelled");
        }
        return new Order(id, customerId, amount, OrderStatus.CANCELLED, createdAt);
    }

    public UUID id() {
        return id;
    }

    public String customerId() {
        return customerId;
    }

    public BigDecimal amount() {
        return amount;
    }

    public OrderStatus status() {
        return status;
    }

    public Instant createdAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Order other)) return false;
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
