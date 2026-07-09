package com.archi.errorhandling;

/**
 * Expected business-rule violation (not found, invalid state transition, invalid input...).
 * Callers at the API boundary are meant to translate this into a client-facing 4xx response.
 */
public final class FunctionalException extends ApplicationException {

    public FunctionalException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public FunctionalException(ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
