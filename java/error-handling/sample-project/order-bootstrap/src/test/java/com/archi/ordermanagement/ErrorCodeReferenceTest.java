package com.archi.ordermanagement;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.archi.errorhandling.ErrorCode;
import com.archi.ordermanagement.core.domain.error.OrderErrorCode;
import com.archi.ordermanagement.messaging.kafka.error.OrderMessagingErrorCode;
import com.archi.ordermanagement.persistence.error.OrderPersistenceErrorCode;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Turns the {@code CTX-MOD-SEQ} reference format documented in {@link ErrorCode#reference()} and
 * sample-project/README.md into a build-enforced rule: format, one {@code CTX-MOD-} prefix per
 * enum, and global uniqueness across the whole app. Runs here because order-bootstrap is the one
 * module with every {@code ErrorCode} enum on its classpath.
 *
 * <p>New {@code ErrorCode} enum in a new adapter module? Add its {@code values()} to
 * {@link #ALL_ERROR_CODES} and its prefix to {@link #eachModuleUsesASingleCtxModPrefix()} —
 * deliberately not classpath-scanned, to keep this test free of an extra reflection dependency.
 */
class ErrorCodeReferenceTest {

    private static final Pattern REFERENCE_PATTERN = Pattern.compile("^[A-Z]{3}-\\d{3}-\\d{4}$");

    private static final List<ErrorCode> ALL_ERROR_CODES = Stream.<ErrorCode[]>of(
                    OrderErrorCode.values(), OrderPersistenceErrorCode.values(), OrderMessagingErrorCode.values())
            .flatMap(Arrays::stream)
            .toList();

    @Test
    void everyErrorCodeReferenceMatchesTheStandardFormat() {
        for (ErrorCode errorCode : ALL_ERROR_CODES) {
            assertTrue(REFERENCE_PATTERN.matcher(errorCode.reference()).matches(),
                    () -> errorCode.getClass().getSimpleName() + "." + errorCode.code()
                            + " has an invalid reference: " + errorCode.reference());
        }
    }

    @Test
    void everyErrorCodeReferenceIsGloballyUnique() {
        Map<String, ErrorCode> byReference = new HashMap<>();
        for (ErrorCode errorCode : ALL_ERROR_CODES) {
            ErrorCode existing = byReference.putIfAbsent(errorCode.reference(), errorCode);
            if (existing != null) {
                fail(errorCode.reference() + " is used by both " + existing.getClass().getSimpleName() + "."
                        + existing.code() + " and " + errorCode.getClass().getSimpleName() + "." + errorCode.code());
            }
        }
    }

    @Test
    void eachModuleUsesASingleCtxModPrefix() {
        assertSinglePrefix(OrderErrorCode.values(), "ORD-001-");
        assertSinglePrefix(OrderPersistenceErrorCode.values(), "ORD-101-");
        assertSinglePrefix(OrderMessagingErrorCode.values(), "ORD-102-");
    }

    private static void assertSinglePrefix(ErrorCode[] errorCodes, String expectedPrefix) {
        for (ErrorCode errorCode : errorCodes) {
            assertTrue(errorCode.reference().startsWith(expectedPrefix),
                    () -> errorCode.code() + " reference " + errorCode.reference()
                            + " does not start with " + expectedPrefix);
        }
    }
}
