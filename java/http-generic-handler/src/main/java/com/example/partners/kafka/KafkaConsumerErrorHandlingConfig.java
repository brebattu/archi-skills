package com.example.partners.kafka;

import com.example.partners.error.FunctionalException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOffWithMaxRetries;

/**
 * Un {@code @KafkaListener} n'est pas un appel qu'on déclenche : Spring pousse les
 * messages, donc {@code execute()} ne s'applique pas ici. La distinction
 * Technical/Functional se branche via {@code addNotRetryableExceptions} :
 * une {@link FunctionalException} part directement en DLT (pas de retry),
 * une erreur technique est rejouée selon le backoff avant de partir en DLT.
 */
@Configuration
public class KafkaConsumerErrorHandlingConfig {

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<Object, Object> template) {
        var recoverer = new DeadLetterPublishingRecoverer(template);
        var backoff = new ExponentialBackOffWithMaxRetries(3);
        backoff.setInitialInterval(500);
        backoff.setMultiplier(2);

        var handler = new DefaultErrorHandler(recoverer, backoff);
        handler.addNotRetryableExceptions(FunctionalException.class); // métier -> DLT direct
        return handler;
    }
}
