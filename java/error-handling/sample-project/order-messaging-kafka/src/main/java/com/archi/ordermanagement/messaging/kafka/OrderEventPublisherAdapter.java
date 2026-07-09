package com.archi.ordermanagement.messaging.kafka;

import com.archi.errorhandling.TechnicalException;
import com.archi.ordermanagement.core.application.port.out.OrderEventPublisherPort;
import com.archi.ordermanagement.core.domain.Order;
import com.archi.ordermanagement.messaging.kafka.error.OrderMessagingErrorCode;
import org.apache.kafka.common.KafkaException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class OrderEventPublisherAdapter implements OrderEventPublisherPort {

    private static final String ORDER_CREATED_TOPIC = "order-created";

    private final KafkaTemplate<String, OrderCreatedMessage> kafkaTemplate;

    public OrderEventPublisherAdapter(KafkaTemplate<String, OrderCreatedMessage> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public void publishOrderCreated(Order order) {
        OrderCreatedMessage message = new OrderCreatedMessage(order.id(), order.customerId(), order.amount(),
                order.createdAt());
        try {
            // Covers synchronous failures (serialization, producer misconfiguration). Failures
            // surfaced asynchronously through the returned CompletableFuture (e.g. broker
            // acknowledgment timeout after this call already returned) would need a compensating
            // strategy (outbox pattern) - out of scope for this pass.
            kafkaTemplate.send(ORDER_CREATED_TOPIC, order.id().toString(), message);
        } catch (KafkaException e) {
            throw new TechnicalException(OrderMessagingErrorCode.ORDER_EVENT_PUBLISHING_FAILURE,
                    "Failed to publish order-created event for order " + order.id(), e);
        }
    }
}
