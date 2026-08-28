package com.example.partners.error;

/**
 * Codes d'erreur portés par {@link TechnicalException} et {@link FunctionalException}.
 * Les codes maison d'un partenaire (ex. {@code PARTNER_B_QUOTA}) s'ajoutent ici.
 */
public enum ErrorCode {
    PARTNER_TIMEOUT,
    PARTNER_5XX,
    PARTNER_RATE_LIMITED,
    PARTNER_4XX,
    PARTNER_UNAUTHORIZED,
    PARTNER_TECHNICAL,
    /** Le circuit breaker a rejeté l'appel (état OPEN) : l'appel n'a même pas atteint le réseau. */
    PARTNER_CIRCUIT_OPEN,

    // Codes maison par partenaire
    PARTNER_B_QUOTA
}
