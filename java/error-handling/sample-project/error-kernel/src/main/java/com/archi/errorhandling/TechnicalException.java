package com.archi.errorhandling;

/**
 * Known, potentially retryable infrastructure failure (database unavailable, broker unreachable,
 * serialization failure...). Never used for programming bugs (NPE, assertion failures): those must
 * stay outside this hierarchy so retry/alerting policies keyed on TechnicalException don't treat a
 * deterministic bug as a transient failure.
 */
public final class TechnicalException extends ApplicationException {

    public TechnicalException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public TechnicalException(ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
