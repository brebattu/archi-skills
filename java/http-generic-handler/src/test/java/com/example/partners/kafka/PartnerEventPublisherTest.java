package com.example.partners.kafka;

import com.example.partners.error.ErrorCode;
import com.example.partners.error.TechnicalException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PartnerEventPublisherTest {

    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);

    @Test
    void publish_succeeds_when_send_completes() {
        when(kafkaTemplate.send(any(String.class), any()))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

        PartnerEventPublisher publisher = new PartnerEventPublisher(kafkaTemplate, looseCircuitBreakerRegistry());

        publisher.publish("topic", "payload");

        verify(kafkaTemplate, times(1)).send("topic", "payload");
    }

    @Test
    void publish_wraps_send_failure_as_technical_exception() {
        CompletableFuture<SendResult<String, Object>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("broker down"));
        when(kafkaTemplate.send(any(String.class), any())).thenReturn(failed);

        PartnerEventPublisher publisher = new PartnerEventPublisher(kafkaTemplate, looseCircuitBreakerRegistry());

        assertThatThrownBy(() -> publisher.publish("topic", "payload"))
                .isInstanceOf(TechnicalException.class)
                .satisfies(e -> assertThat(((TechnicalException) e).getCode())
                        .isEqualTo(ErrorCode.PARTNER_TECHNICAL));
    }

    @Test
    void circuit_breaker_opens_after_repeated_failures_and_stops_calling_kafka_template() {
        CompletableFuture<SendResult<String, Object>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("broker down"));
        when(kafkaTemplate.send(any(String.class), any())).thenReturn(failed);

        CircuitBreakerConfig cbConfig = CircuitBreakerConfig.custom()
                .slidingWindowSize(4)
                .minimumNumberOfCalls(4)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .build();
        PartnerEventPublisher publisher = new PartnerEventPublisher(kafkaTemplate, CircuitBreakerRegistry.of(cbConfig));

        for (int i = 0; i < 4; i++) {
            assertThatThrownBy(() -> publisher.publish("topic", "payload")).isInstanceOf(TechnicalException.class);
        }

        assertThatThrownBy(() -> publisher.publish("topic", "payload"))
                .isInstanceOf(TechnicalException.class)
                .satisfies(e -> assertThat(((TechnicalException) e).getCode())
                        .isEqualTo(ErrorCode.PARTNER_CIRCUIT_OPEN));

        // 4 tentatives réelles seulement : le 5e appel est court-circuité par le breaker ouvert.
        verify(kafkaTemplate, times(4)).send("topic", "payload");
    }

    private CircuitBreakerRegistry looseCircuitBreakerRegistry() {
        return CircuitBreakerRegistry.of(CircuitBreakerConfig.custom()
                .slidingWindowSize(100)
                .minimumNumberOfCalls(100)
                .build());
    }
}
