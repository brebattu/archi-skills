package com.archi.errorhandling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ApplicationExceptionTest {

    private enum TestErrorCode implements ErrorCode {
        SOMETHING_WENT_WRONG;

        @Override
        public String code() {
            return name();
        }
    }

    @Test
    void should_carryErrorCodeMessageAndCause_when_allProvided() {
        Throwable cause = new IllegalStateException("root cause");

        TechnicalException exception =
                new TechnicalException(TestErrorCode.SOMETHING_WENT_WRONG, "boom", cause);

        assertSame(TestErrorCode.SOMETHING_WENT_WRONG, exception.errorCode());
        assertEquals("boom", exception.getMessage());
        assertSame(cause, exception.getCause());
    }

    @Test
    void should_haveNullCause_when_causeOmitted() {
        FunctionalException exception = new FunctionalException(TestErrorCode.SOMETHING_WENT_WRONG, "boom");

        assertNull(exception.getCause());
    }

    @Test
    void should_throwNullPointerException_when_errorCodeIsNull() {
        assertThrows(NullPointerException.class, () -> new FunctionalException(null, "boom"));
    }
}
