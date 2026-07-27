package com.archi.errorhandling;

/**
 * Contract implemented by one enum per bounded context (e.g. an {@code OrderErrorCode} in an
 * order module), each value describing one precise, documented incident.
 */
public interface ErrorCode {

    String code();

    /**
     * Stable, human-typeable identifier for logs, support tickets and i18n keys, in the form
     * {@code <CTX>-<MOD>-<SEQ>} (e.g. {@code ORD-001-0003}). Distinct from {@code code()}: the
     * latter is symbolic and serves compile-time switches / API payloads, this one is a durable
     * cross-system reference.
     * <ul>
     *   <li>{@code CTX} — 3-letter bounded context code, one per Maven aggregate (e.g. {@code ORD}
     *       for order-management). Assigned once; a new bounded context takes its own prefix so
     *       references stay unambiguous when grepped across the whole platform's logs.</li>
     *   <li>{@code MOD} — 3-digit module id, one per {@code ErrorCode} enum (so one per module
     *       that throws its own errors, mirroring the "one enum per module" rule). Range
     *       convention: {@code 000-099} for core/functional modules, {@code 100-199} for outbound
     *       (driven) adapters, {@code 200-299} for inbound (driving) adapters — the range alone
     *       tells a reader whether a reference is functional or technical.</li>
     *   <li>{@code SEQ} — 4-digit sequence, assigned as a literal per constant (never derived from
     *       {@code ordinal()}, so reordering the enum can't silently change a reference already
     *       shipped in a log line). Append-only: once assigned, a number is never reused, even
     *       after the constant it named is deprecated and removed.</li>
     * </ul>
     */
    String reference();
}
