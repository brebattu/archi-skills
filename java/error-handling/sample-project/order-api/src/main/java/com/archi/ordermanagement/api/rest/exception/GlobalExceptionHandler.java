package com.archi.ordermanagement.api.rest.exception;

import com.archi.errorhandling.FunctionalException;
import com.archi.errorhandling.TechnicalException;
import com.archi.ordermanagement.core.domain.error.OrderErrorCode;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Four handlers, each with a distinct responsibility:
 * <ul>
 *   <li>{@link FunctionalException} — expected business-rule violation, mapped to a precise 4xx.</li>
 *   <li>{@link TechnicalException} — known infra failure, always 500, never leaks the raw cause.</li>
 *   <li>{@link MethodArgumentNotValidException} — malformed request, not a business rule.</li>
 *   <li>{@link Exception} — anything else is treated as a bug, never confused with TechnicalException
 *       (a retry/alerting policy keyed on TechnicalException must never see a bug).</li>
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(FunctionalException.class)
    public ProblemDetail handleFunctional(FunctionalException ex) {
        // Safe here: order-api only ever handles order-core's ErrorCode implementation. A second
        // bounded context would need its own handler/switch over its own ErrorCode type.
        HttpStatus status = toHttpStatus((OrderErrorCode) ex.errorCode());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
        problem.setProperty("code", ex.errorCode().code());
        return problem;
    }

    @ExceptionHandler(TechnicalException.class)
    public ProblemDetail handleTechnical(TechnicalException ex) {
        String incidentId = UUID.randomUUID().toString();
        // MDC keys picked up by the console pattern (see order-bootstrap's application.yml), so
        // log aggregation can filter/query on errorCode/incidentId as structured fields instead of
        // grepping free text.
        MDC.put("errorCode", ex.errorCode().code());
        MDC.put("incidentId", incidentId);
        try {
            log.error("Technical error", ex);
        } finally {
            MDC.remove("errorCode");
            MDC.remove("incidentId");
        }

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Technical error");
        problem.setProperty("code", ex.errorCode().code());
        // Returned to the client so a support ticket referencing this id can be grepped straight
        // out of the logs, without needing to expose the raw exception detail.
        problem.setProperty("incidentId", incidentId);
        return problem;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        List<String> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                .toList();
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Invalid request");
        problem.setProperty("errors", errors);
        return problem;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        log.error("Unexpected bug", ex);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error");
    }

    // Exhaustive, no default: adding an OrderErrorCode value without mapping it here is a compile error.
    private static HttpStatus toHttpStatus(OrderErrorCode errorCode) {
        return switch (errorCode) {
            case ORDER_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case ORDER_ALREADY_CANCELLED -> HttpStatus.CONFLICT;
            case INVALID_CUSTOMER_ID, INVALID_ORDER_AMOUNT -> HttpStatus.BAD_REQUEST;
            case ORDER_CREATED_NOTIFICATION_FAILED -> HttpStatus.BAD_GATEWAY;
        };
    }
}
