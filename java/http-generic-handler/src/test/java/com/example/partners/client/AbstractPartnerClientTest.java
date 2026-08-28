package com.example.partners.client;

import com.example.partners.config.PartnersProperties;
import com.example.partners.error.ErrorCode;
import com.example.partners.error.FunctionalException;
import com.example.partners.error.TechnicalException;
import com.example.partners.support.StubHttpServer;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AbstractPartnerClientTest {

    private StubHttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.close();
        }
    }

    private TestPartnerClient client(Set<ErrorCode> retryableCodes) {
        return client(retryableCodes, null, defaultCbConfig());
    }

    private TestPartnerClient client(Set<ErrorCode> retryableCodes, CircuitBreakerConfig cbConfig) {
        return client(retryableCodes, null, cbConfig);
    }

    /** {@code circuitBreakerCodes} null = comportement par défaut (toute TechnicalException compte). */
    private TestPartnerClient client(Set<ErrorCode> retryableCodes, Set<ErrorCode> circuitBreakerCodes,
                                      CircuitBreakerConfig cbConfig) {
        server = new StubHttpServer();
        PartnersProperties.ClientConfig cfg = new PartnersProperties.ClientConfig(
                server.baseUrl(), Duration.ofMillis(300), Duration.ofMillis(300),
                retryableCodes, circuitBreakerCodes);

        RetryConfig retryConfig = RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofMillis(10))
                .build();
        RetryRegistry retryRegistry = RetryRegistry.of(retryConfig);
        CircuitBreakerRegistry cbRegistry = CircuitBreakerRegistry.of(cbConfig);

        return new TestPartnerClient(cfg, retryRegistry, cbRegistry);
    }

    private CircuitBreakerConfig defaultCbConfig() {
        // Fenêtre large : les scénarios non dédiés au breaker ne doivent jamais l'ouvrir.
        return CircuitBreakerConfig.custom()
                .slidingWindowSize(100)
                .minimumNumberOfCalls(100)
                .build();
    }

    @Test
    void succeeds_on_first_try() {
        TestPartnerClient client = client(Set.of());
        server.enqueue(200, "\"ok\"");

        String result = client.callWithRetry();

        assertThat(result).isEqualTo("ok");
        assertThat(server.requestCount()).isEqualTo(1);
    }

    @Test
    void retries_technical_error_then_succeeds() {
        TestPartnerClient client = client(Set.of(ErrorCode.PARTNER_5XX));
        server.enqueue(500, "");
        server.enqueue(500, "");
        server.enqueue(200, "\"ok\"");

        String result = client.callWithRetry();

        assertThat(result).isEqualTo("ok");
        assertThat(server.requestCount()).isEqualTo(3);
    }

    @Test
    void exhausts_retries_and_throws_technical_exception_with_last_code() {
        TestPartnerClient client = client(Set.of(ErrorCode.PARTNER_RATE_LIMITED));
        server.setDefaultResponse(429, "");

        assertThatThrownBy(client::callWithRetry)
                .isInstanceOf(TechnicalException.class)
                .satisfies(e -> assertThat(((TechnicalException) e).getCode())
                        .isEqualTo(ErrorCode.PARTNER_RATE_LIMITED));
        assertThat(server.requestCount()).isEqualTo(3); // max-attempts
    }

    @Test
    void functional_error_is_never_retried() {
        TestPartnerClient client = client(Set.of(ErrorCode.PARTNER_5XX, ErrorCode.PARTNER_RATE_LIMITED));
        server.enqueue(401, "");

        assertThatThrownBy(client::callWithRetry)
                .isInstanceOf(FunctionalException.class)
                .satisfies(e -> assertThat(((FunctionalException) e).getCode())
                        .isEqualTo(ErrorCode.PARTNER_UNAUTHORIZED));
        assertThat(server.requestCount()).isEqualTo(1);
    }

    @Test
    void generic_4xx_maps_to_functional_exception() {
        TestPartnerClient client = client(Set.of());
        server.enqueue(404, "");

        assertThatThrownBy(client::callWithRetry)
                .isInstanceOf(FunctionalException.class)
                .satisfies(e -> assertThat(((FunctionalException) e).getCode())
                        .isEqualTo(ErrorCode.PARTNER_4XX));
        assertThat(server.requestCount()).isEqualTo(1);
    }

    @Test
    void technical_error_not_in_retryable_set_is_not_retried() {
        // 5xx est technique mais absent de la liste retryable du partenaire -> pas de retry.
        TestPartnerClient client = client(Set.of(ErrorCode.PARTNER_RATE_LIMITED));
        server.enqueue(500, "");

        assertThatThrownBy(client::callWithRetry).isInstanceOf(TechnicalException.class);
        assertThat(server.requestCount()).isEqualTo(1);
    }

    @Test
    void network_timeout_maps_to_technical_exception_and_is_retried() {
        TestPartnerClient client = client(Set.of(ErrorCode.PARTNER_TIMEOUT));
        server.hangResponses(2000); // > readTimeout (300ms)

        assertThatThrownBy(client::callWithRetry)
                .isInstanceOf(TechnicalException.class)
                .satisfies(e -> assertThat(((TechnicalException) e).getCode())
                        .isEqualTo(ErrorCode.PARTNER_TIMEOUT));
        assertThat(server.requestCount()).isEqualTo(3); // retried up to max-attempts
    }

    @Test
    void execute_no_retry_calls_server_only_once_on_technical_error() {
        server = new StubHttpServer();
        PartnersProperties.ClientConfig cfg = new PartnersProperties.ClientConfig(
                server.baseUrl(), Duration.ofMillis(300), Duration.ofMillis(300),
                Set.of(ErrorCode.PARTNER_5XX), null);
        RetryRegistry retryRegistry = RetryRegistry.of(RetryConfig.custom()
                .maxAttempts(3).waitDuration(Duration.ofMillis(10)).build());
        CircuitBreakerRegistry cbRegistry = CircuitBreakerRegistry.of(defaultCbConfig());
        TestPartnerClient client = new TestPartnerClient(cfg, retryRegistry, cbRegistry);

        server.enqueue(500, "");

        assertThatThrownBy(client::callNoRetry).isInstanceOf(TechnicalException.class);
        assertThat(server.requestCount()).isEqualTo(1);
    }

    @Test
    void circuit_breaker_opens_after_failure_threshold_and_short_circuits_without_calling_server() {
        CircuitBreakerConfig cbConfig = CircuitBreakerConfig.custom()
                .slidingWindowSize(4)
                .minimumNumberOfCalls(4)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .build();
        TestPartnerClient client = client(Set.of(), cbConfig); // pas de retry pour compter précisément les échecs
        server.setDefaultResponse(500, "");

        // 4 échecs consécutifs (minimumNumberOfCalls) -> le breaker doit s'ouvrir.
        // retryableCodes est vide : chaque appel ne consomme qu'une seule requête serveur.
        for (int i = 0; i < 4; i++) {
            assertThatThrownBy(client::callWithRetry).isInstanceOf(TechnicalException.class);
        }
        int callsBeforeOpen = server.requestCount();

        assertThatThrownBy(client::callWithRetry)
                .isInstanceOf(TechnicalException.class)
                .satisfies(e -> assertThat(((TechnicalException) e).getCode())
                        .isEqualTo(ErrorCode.PARTNER_CIRCUIT_OPEN));

        // Le circuit est ouvert : aucun appel réseau supplémentaire.
        assertThat(server.requestCount()).isEqualTo(callsBeforeOpen);
    }

    @Test
    void circuit_breaker_codes_exclude_non_listed_technical_errors_from_failure_rate() {
        CircuitBreakerConfig cbConfig = CircuitBreakerConfig.custom()
                .slidingWindowSize(4).minimumNumberOfCalls(4).failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .build();
        // Seul PARTNER_5XX compte pour ce partenaire ; PARTNER_RATE_LIMITED (429) est technique
        // mais absent de la liste -> resilience4j le traite comme un succès côté breaker.
        TestPartnerClient client = client(Set.of(), Set.of(ErrorCode.PARTNER_5XX), cbConfig);
        server.setDefaultResponse(429, "");

        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(client::callWithRetry)
                    .isInstanceOf(TechnicalException.class)
                    .satisfies(e -> assertThat(((TechnicalException) e).getCode())
                            .isEqualTo(ErrorCode.PARTNER_RATE_LIMITED));
        }

        // Toujours des appels réseau réels (pas de PARTNER_CIRCUIT_OPEN) : le breaker est resté fermé.
        assertThat(server.requestCount()).isEqualTo(5);
    }

    @Test
    void circuit_breaker_codes_include_listed_technical_errors_in_failure_rate() {
        CircuitBreakerConfig cbConfig = CircuitBreakerConfig.custom()
                .slidingWindowSize(4).minimumNumberOfCalls(4).failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .build();
        TestPartnerClient client = client(Set.of(), Set.of(ErrorCode.PARTNER_5XX), cbConfig);
        server.setDefaultResponse(500, "");

        for (int i = 0; i < 4; i++) {
            assertThatThrownBy(client::callWithRetry).isInstanceOf(TechnicalException.class);
        }
        int callsBeforeOpen = server.requestCount();

        assertThatThrownBy(client::callWithRetry)
                .isInstanceOf(TechnicalException.class)
                .satisfies(e -> assertThat(((TechnicalException) e).getCode())
                        .isEqualTo(ErrorCode.PARTNER_CIRCUIT_OPEN));

        // Le breaker s'est ouvert sur des codes listés dans circuitBreakerCodes : plus d'appel réseau.
        assertThat(server.requestCount()).isEqualTo(callsBeforeOpen);
    }
}
