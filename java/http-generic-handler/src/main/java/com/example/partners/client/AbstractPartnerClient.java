package com.example.partners.client;

import com.example.partners.config.PartnersProperties;
import com.example.partners.error.ErrorCode;
import com.example.partners.error.FunctionalException;
import com.example.partners.error.TechnicalException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Porte toute la mécanique technique commune à un appel partenaire : timeouts,
 * log de chaque appel HTTP, mapping HTTP -&gt; exception métier (une seule fois,
 * ici), retry sélectif et circuit breaker. Les classes filles n'écrivent que
 * leurs endpoints et, ponctuellement, {@link #handleError} pour un code
 * d'erreur maison.
 *
 * <p>Aucune classe fille ne doit jamais voir une exception Spring ou resilience4j :
 * {@link #execute} et {@link #executeNoRetry} ne laissent transiter que
 * {@link TechnicalException} / {@link FunctionalException}.
 */
public abstract class AbstractPartnerClient {

    protected final RestClient restClient;
    private final Retry retry;
    private final CircuitBreaker circuitBreaker;

    protected AbstractPartnerClient(String partnerName,
                                     PartnersProperties.ClientConfig cfg,
                                     RetryRegistry retryRegistry,
                                     CircuitBreakerRegistry circuitBreakerRegistry) {
        Logger callLog = LoggerFactory.getLogger(AbstractPartnerClient.class.getName() + "." + partnerName);

        this.restClient = RestClient.builder()
                .baseUrl(cfg.baseUrl())
                .requestFactory(ClientHttpRequestFactoryBuilder.detect()
                        .build(ClientHttpRequestFactorySettings.defaults()
                                .withConnectTimeout(cfg.connectTimeout())
                                .withReadTimeout(cfg.readTimeout())))
                .requestInterceptor(loggingInterceptor(callLog))
                .defaultStatusHandler(new StatusHandlerAdapter())
                .build();

        Set<ErrorCode> retryableCodes = cfg.retryableCodes();
        this.retry = retryRegistry.retry(partnerName + "-managed",
                RetryConfig.from(retryRegistry.getConfiguration(partnerName)
                                .orElse(retryRegistry.getDefaultConfig()))
                        .retryOnException(t -> t instanceof TechnicalException e
                                && retryableCodes.contains(e.getCode()))
                        .build());

        Set<ErrorCode> circuitBreakerCodes = cfg.circuitBreakerCodes(); // null = toute TechnicalException compte
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker(partnerName + "-managed",
                CircuitBreakerConfig.from(circuitBreakerRegistry.getConfiguration(partnerName)
                                .orElse(circuitBreakerRegistry.getDefaultConfig()))
                        .recordException(t -> t instanceof TechnicalException e
                                && (circuitBreakerCodes == null || circuitBreakerCodes.contains(e.getCode())))
                        .build());
    }

    /**
     * Mapping HTTP -&gt; exception métier. Un partenaire avec un code d'erreur
     * maison override, traite son cas, puis appelle {@code super.handleError(...)}
     * pour retomber sur ce mapping générique. Ne rien lever laisse la réponse
     * être traitée comme un succès : {@code retrieve().body(...)} désérialise
     * alors le corps normalement — utile pour renvoyer tel quel un statut
     * d'erreur porteur d'un corps exploitable côté appelant.
     */
    protected void handleError(HttpStatusCode status, ClientHttpResponse response) throws IOException {
        if (status.is5xxServerError()) {
            throw new TechnicalException(ErrorCode.PARTNER_5XX, "5xx");
        }
        if (status.value() == 429) {
            throw new TechnicalException(ErrorCode.PARTNER_RATE_LIMITED, "429");
        }
        if (status.value() == 401 || status.value() == 403) {
            throw new FunctionalException(ErrorCode.PARTNER_UNAUTHORIZED, "auth");
        }
        throw new FunctionalException(ErrorCode.PARTNER_4XX, "4xx " + status.value());
    }

    /**
     * Signale un échec au circuit breaker sans lever d'exception. Réservé aux
     * overrides de {@link #handleError} en mode "passthrough" (qui ne lèvent
     * plus rien pour laisser la réponse repartir telle quelle vers l'appelant)
     * mais qui veulent quand même que certains codes techniques comptent pour
     * le breaker. Respecte {@code circuitBreakerCodes} : si le code n'est pas
     * dans la liste configurée pour ce partenaire, resilience4j ne le compte
     * pas comme un échec (il est traité comme un succès, comportement standard
     * du predicate {@code recordException} quand il renvoie {@code false}).
     */
    protected void recordCircuitBreakerFailure(ErrorCode code) {
        circuitBreaker.onError(0, TimeUnit.NANOSECONDS,
                new TechnicalException(code, "signalé manuellement (passthrough)"));
    }

    /**
     * Appel avec retry + circuit breaker. Réservé aux opérations idempotentes.
     */
    protected <T> T execute(Supplier<T> call) {
        return runProtected(call, true);
    }

    /**
     * Appel avec circuit breaker seul, sans retry. À utiliser pour les opérations
     * non idempotentes (ex. POST de création) où rejouer l'appel serait dangereux,
     * même si l'erreur obtenue porte un code techniquement retryable.
     */
    protected <T> T executeNoRetry(Supplier<T> call) {
        return runProtected(call, false);
    }

    private <T> T runProtected(Supplier<T> call, boolean withRetry) {
        Supplier<T> withNetworkMapping = () -> {
            try {
                return call.get(); // erreurs HTTP déjà mappées par handleError
            } catch (ResourceAccessException e) { // timeout/connexion : levé avant réponse
                throw new TechnicalException(ErrorCode.PARTNER_TIMEOUT, "réseau", e);
            }
        };
        Supplier<T> withCircuitBreaker = CircuitBreaker.decorateSupplier(circuitBreaker, withNetworkMapping);
        Supplier<T> decorated = withRetry ? Retry.decorateSupplier(retry, withCircuitBreaker) : withCircuitBreaker;

        try {
            return decorated.get();
        } catch (CallNotPermittedException e) {
            throw new TechnicalException(ErrorCode.PARTNER_CIRCUIT_OPEN, "circuit breaker ouvert", e);
        }
    }

    private static ClientHttpRequestInterceptor loggingInterceptor(Logger callLog) {
        return (request, body, execution) -> {
            long start = System.nanoTime();
            try {
                ClientHttpResponse response = execution.execute(request, body);
                callLog.info("{} {} -> {} ({} ms)", request.getMethod(), request.getURI(),
                        response.getStatusCode().value(), elapsedMillis(start));
                return response;
            } catch (IOException e) {
                callLog.warn("{} {} -> échec réseau ({} ms) : {}", request.getMethod(), request.getURI(),
                        elapsedMillis(start), e.getMessage());
                throw e;
            }
        };
    }

    private static long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    private class StatusHandlerAdapter implements ResponseErrorHandler {
        @Override
        public boolean hasError(ClientHttpResponse response) throws IOException {
            return response.getStatusCode().isError();
        }

        @Override
        public void handleError(ClientHttpResponse response) throws IOException {
            AbstractPartnerClient.this.handleError(response.getStatusCode(), response);
        }
    }
}
