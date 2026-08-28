package com.example.partners.client;

import com.example.partners.config.PartnersProperties;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryRegistry;

/**
 * Double de test exposant execute()/executeNoRetry() pour valider le socle
 * indépendamment de tout partenaire réel.
 */
class TestPartnerClient extends AbstractPartnerClient {

    TestPartnerClient(PartnersProperties.ClientConfig cfg, RetryRegistry retryRegistry,
                       CircuitBreakerRegistry circuitBreakerRegistry) {
        super("partnerTest", cfg, retryRegistry, circuitBreakerRegistry);
    }

    String callWithRetry() {
        return execute(() -> restClient.get().uri("/thing").retrieve().body(String.class));
    }

    String callNoRetry() {
        return executeNoRetry(() -> restClient.get().uri("/thing").retrieve().body(String.class));
    }
}
