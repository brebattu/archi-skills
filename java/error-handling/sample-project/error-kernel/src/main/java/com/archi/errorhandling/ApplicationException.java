package com.archi.errorhandling;

import java.util.Objects;

/**
 * Single base for every thrown application error. Deliberately not extended per use case: the
 * precise incident lives in the {@link ErrorCode}, not in the class hierarchy. Only two concrete
 * subclasses exist, see {@link FunctionalException} and {@link TechnicalException}.
 */
public abstract sealed class ApplicationException extends RuntimeException
        permits FunctionalException, TechnicalException {

    private final ErrorCode errorCode;

    protected ApplicationException(ErrorCode errorCode, String message) {
        this(errorCode, message, null);
    }

    protected ApplicationException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode must not be null");
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    /**
     * Checks the carried error code against a raw code string rather than a Java type. Lets a
     * caller react to a specific incident (e.g. a module deciding how to handle a failure coming
     * from another module's port) without a compile-time dependency on that module's own
     * ErrorCode enum. Trade-off: no compiler-checked consistency between the string used here and
     * the enum constant it is meant to match — a typo on either side fails silently at runtime.
     */
    public boolean hasErrorCode(String code) {
        return errorCode.code().equals(code);
    }
}
