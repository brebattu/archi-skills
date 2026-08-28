package com.example.partners.client.partnera;

import com.example.partners.client.AbstractPartnerClient;
import com.example.partners.config.PartnersProperties;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import org.springframework.stereotype.Component;

/**
 * Client standard : n'override aucun hook, utilise uniquement le mapping générique du socle.
 */
@Component
public class PartnerAClient extends AbstractPartnerClient {

    public PartnerAClient(PartnersProperties properties, RetryRegistry retryRegistry,
                           CircuitBreakerRegistry circuitBreakerRegistry) {
        super("partnerA", properties.clients().get("partnerA"), retryRegistry, circuitBreakerRegistry);
    }

    /** GET idempotent : retry + circuit breaker. */
    public OrderDto getOrder(String id) {
        return execute(() -> restClient.get().uri("/orders/{id}", id).retrieve().body(OrderDto.class));
    }

    /** POST non idempotent : circuit breaker seul, pas de retry. */
    public OrderDto createOrder(NewOrderDto newOrder) {
        return executeNoRetry(() -> restClient.post().uri("/orders")
                .body(newOrder)
                .retrieve()
                .body(OrderDto.class));
    }
}
