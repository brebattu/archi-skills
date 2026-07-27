package com.archi.ordermanagement.core.domain.error;

import com.archi.errorhandling.ErrorCode;

/**
 * One value per precise business-rule violation owned by order-core itself. Technical incidents
 * belong to the adapter that produces them (see e.g. OrderPersistenceErrorCode in
 * order-persistence, OrderMessagingErrorCode in order-messaging-kafka), not here: order-core must
 * stay purely functional.
 */
public enum OrderErrorCode implements ErrorCode {

    ORDER_NOT_FOUND("ORD-001-0001"),
    INVALID_CUSTOMER_ID("ORD-001-0002"),
    INVALID_ORDER_AMOUNT("ORD-001-0003"),
    ORDER_ALREADY_CANCELLED("ORD-001-0004"),
    ORDER_CREATED_NOTIFICATION_FAILED("ORD-001-0005"),
    // Thrown by order-persistence's OrderRepositoryAdapter, not by order-core itself: a PK
    // constraint violation has a single possible business meaning ("this order already exists")
    // and order-persistence has no ErrorCode of its own to spend on a core-owned case. See
    // README point 6.
    ORDER_ALREADY_EXISTS("ORD-001-0006");

    private final String reference;

    OrderErrorCode(String reference) {
        this.reference = reference;
    }

    @Override
    public String code() {
        return name();
    }

    @Override
    public String reference() {
        return reference;
    }
}
