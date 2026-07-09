package com.archi.errorhandling;

/**
 * Contract implemented by one enum per bounded context (e.g. an {@code OrderErrorCode} in an
 * order module), each value describing one precise, documented incident.
 */
public interface ErrorCode {

    String code();
}
