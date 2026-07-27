package com.archi.ordermanagement.persistence.error;

import com.archi.errorhandling.ErrorCode;

/** Technical incidents owned by this adapter, distinct from order-core's functional codes. */
public enum OrderPersistenceErrorCode implements ErrorCode {

    ORDER_PERSISTENCE_FAILURE("ORD-101-0001");

    private final String reference;

    OrderPersistenceErrorCode(String reference) {
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
