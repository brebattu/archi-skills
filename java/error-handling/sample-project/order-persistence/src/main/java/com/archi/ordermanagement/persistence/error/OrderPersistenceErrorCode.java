package com.archi.ordermanagement.persistence.error;

import com.archi.errorhandling.ErrorCode;

/** Technical incidents owned by this adapter, distinct from order-core's functional codes. */
public enum OrderPersistenceErrorCode implements ErrorCode {

    ORDER_PERSISTENCE_FAILURE;

    @Override
    public String code() {
        return name();
    }
}
