package com.example.partners.error;

/**
 * Panne technique (réseau, timeout, 5xx, rate limit, ...).
 * Peut être retryable selon le {@link ErrorCode} et la config du partenaire ;
 * alimente le circuit breaker.
 */
public class TechnicalException extends RuntimeException {

    private final ErrorCode code;

    public TechnicalException(ErrorCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public TechnicalException(ErrorCode code, String message) {
        this(code, message, null);
    }

    public ErrorCode getCode() {
        return code;
    }
}
