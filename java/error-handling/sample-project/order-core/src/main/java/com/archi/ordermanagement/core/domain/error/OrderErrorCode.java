package com.archi.ordermanagement.core.domain.error;

import com.archi.errorhandling.ErrorCode;

/**
 * One value per precise business-rule violation owned by order-core itself. Technical incidents
 * belong to the adapter that produces them (see e.g. OrderPersistenceErrorCode in
 * order-persistence, OrderMessagingErrorCode in order-messaging-kafka), not here: order-core must
 * stay purely functional.
 */
public enum OrderErrorCode implements ErrorCode {

    ORDER_NOT_FOUND,
    INVALID_CUSTOMER_ID,
    INVALID_ORDER_AMOUNT,
    ORDER_ALREADY_CANCELLED,
    ORDER_CREATED_NOTIFICATION_FAILED;

    @Override
    public String code() {
        return name();
    }
}
