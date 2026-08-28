package com.example.partners.error;

/**
 * Erreur métier (4xx non technique, refus applicatif, ...).
 * Jamais de retry, n'alimente jamais le circuit breaker.
 */
public class FunctionalException extends RuntimeException {

    private final ErrorCode code;

    public FunctionalException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public ErrorCode getCode() {
        return code;
    }
}
