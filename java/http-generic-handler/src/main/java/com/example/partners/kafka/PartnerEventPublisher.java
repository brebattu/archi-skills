package com.example.partners.kafka;

import com.example.partners.error.ErrorCode;
import com.example.partners.error.TechnicalException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

/**
 * Publication Kafka synchrone réutilisant la classification Technical/Functional du socle.
 *
 * <p>Le producer Kafka gère déjà ses propres retries de livraison ({@code retries},
 * {@code delivery.timeout.ms}, {@code acks}) : on n'empile PAS de retry Resilience4j
 * par-dessus. Seul le circuit breaker est appliqué, pour couper quand le broker est
 * durablement down.
 */
@Component
public class PartnerEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final CircuitBreaker circuitBreaker;

    public PartnerEventPublisher(KafkaTemplate<String, Object> kafkaTemplate,
                                  CircuitBreakerRegistry circuitBreakerRegistry) {
        this.kafkaTemplate = kafkaTemplate;
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker("kafka-producer",
                CircuitBreakerConfig.from(circuitBreakerRegistry.getConfiguration("kafka-producer")
                                .orElse(circuitBreakerRegistry.getDefaultConfig()))
                        .recordException(t -> t instanceof TechnicalException)
                        .build());
    }

    public void publish(String topic, Object payload) {
        Supplier<Void> send = () -> {
            try {
                kafkaTemplate.send(topic, payload).get();
                return null;
            } catch (ExecutionException e) {
                throw new TechnicalException(ErrorCode.PARTNER_TECHNICAL, "échec envoi Kafka", e.getCause());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new TechnicalException(ErrorCode.PARTNER_TECHNICAL, "envoi Kafka interrompu", e);
            }
        };

        try {
            CircuitBreaker.decorateSupplier(circuitBreaker, send).get();
        } catch (CallNotPermittedException e) {
            throw new TechnicalException(ErrorCode.PARTNER_CIRCUIT_OPEN, "circuit breaker ouvert (kafka)", e);
        }
    }
}
