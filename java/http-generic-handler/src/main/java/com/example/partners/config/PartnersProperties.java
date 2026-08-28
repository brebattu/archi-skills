package com.example.partners.config;

import com.example.partners.error.ErrorCode;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Map;
import java.util.Set;

/**
 * Configuration par partenaire : base-url, timeouts, et surtout les listes de
 * {@link ErrorCode} qui pilotent le retry et le circuit breaker — ces listes
 * appartiennent au partenaire, pas au socle.
 */
@ConfigurationProperties(prefix = "partners")
public record PartnersProperties(Map<String, ClientConfig> clients) {

    public record ClientConfig(
            String baseUrl,
            Duration connectTimeout,
            Duration readTimeout,
            Set<ErrorCode> retryableCodes,
            /**
             * Codes comptant comme échec pour le circuit breaker. Optionnel :
             * si absent (null), TOUTE {@code TechnicalException} compte comme
             * échec (comportement par défaut, pas besoin de le redéclarer pour
             * chaque partenaire). Ne le renseigner que si un partenaire doit
             * restreindre la liste (ex. un partenaire en passthrough qui ne
             * lève plus d'exception HTTP et pilote le breaker manuellement).
             */
            Set<ErrorCode> circuitBreakerCodes
    ) {
    }
}
