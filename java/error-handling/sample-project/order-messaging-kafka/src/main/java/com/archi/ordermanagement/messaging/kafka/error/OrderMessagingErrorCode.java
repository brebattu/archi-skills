package com.archi.ordermanagement.messaging.kafka.error;

import com.archi.errorhandling.ErrorCode;

/** Technical incidents owned by this adapter, distinct from order-core's functional codes. */
public enum OrderMessagingErrorCode implements ErrorCode {

    ORDER_EVENT_PUBLISHING_FAILURE("ORD-102-0001");

    private final String reference;

    OrderMessagingErrorCode(String reference) {
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
