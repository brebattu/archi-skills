package com.archi.ordermanagement.core.application.service;

import com.archi.ordermanagement.core.application.port.in.CancelOrderUseCase;
import com.archi.ordermanagement.core.application.port.in.CreateOrderCommand;
import com.archi.ordermanagement.core.application.port.in.CreateOrderUseCase;
import com.archi.ordermanagement.core.application.port.in.DeleteOrderUseCase;
import com.archi.ordermanagement.core.application.port.in.GetOrderUseCase;
import com.archi.ordermanagement.core.application.port.in.ListOrdersUseCase;
import com.archi.errorhandling.FunctionalException;
import com.archi.errorhandling.TechnicalException;
import com.archi.ordermanagement.core.application.port.out.OrderEventPublisherPort;
import com.archi.ordermanagement.core.application.port.out.OrderRepositoryPort;
import com.archi.ordermanagement.core.domain.Order;
import com.archi.ordermanagement.core.domain.error.OrderErrorCode;
import com.archi.ordermanagement.core.domain.paging.PageRequest;
import com.archi.ordermanagement.core.domain.paging.PageResult;
import java.util.UUID;

/**
 * Single implementation of all order use cases. Depends only on outbound ports, never on
 * adapter/framework types. Wired manually as a Spring bean by order-bootstrap (see
 * CoreBeansConfig) since this module has no Spring dependency of its own.
 */
public final class OrderService implements CreateOrderUseCase, GetOrderUseCase, ListOrdersUseCase,
        CancelOrderUseCase, DeleteOrderUseCase {

    // Mirrors OrderMessagingErrorCode.ORDER_EVENT_PUBLISHING_FAILURE.code() from order-messaging-kafka,
    // without a compile-time dependency on that module. Trade-off: a typo on either side wouldn't
    // be caught by the compiler, only at runtime.
    private static final String EVENT_PUBLISHING_FAILURE_CODE = "ORDER_EVENT_PUBLISHING_FAILURE";

    private final OrderRepositoryPort orderRepository;
    private final OrderEventPublisherPort orderEventPublisher;

    public OrderService(OrderRepositoryPort orderRepository, OrderEventPublisherPort orderEventPublisher) {
        this.orderRepository = orderRepository;
        this.orderEventPublisher = orderEventPublisher;
    }

    @Override
    public Order createOrder(CreateOrderCommand command) {
        Order order = Order.create(command.customerId(), command.amount());
        Order saved = orderRepository.save(order);
        try {
            orderEventPublisher.publishOrderCreated(saved);
        } catch (TechnicalException e) {
            if (e.hasErrorCode(EVENT_PUBLISHING_FAILURE_CODE)) {
                // The order genuinely exists at this point: this is a distinct, core-owned case,
                // not a plain infra failure the caller should retry the whole request for.
                throw new FunctionalException(OrderErrorCode.ORDER_CREATED_NOTIFICATION_FAILED,
                        "Order " + saved.id() + " was created but its creation notification could not be sent", e);
            }
            throw e;
        }
        return saved;
    }

    @Override
    public Order getOrder(UUID orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new FunctionalException(OrderErrorCode.ORDER_NOT_FOUND, "Order not found: " + orderId));
    }

    @Override
    public PageResult<Order> listOrders(PageRequest pageRequest) {
        return orderRepository.findAll(pageRequest);
    }

    @Override
    public Order cancelOrder(UUID orderId) {
        Order order = getOrder(orderId);
        return orderRepository.save(order.cancel());
    }

    @Override
    public void deleteOrder(UUID orderId) {
        getOrder(orderId);
        orderRepository.deleteById(orderId);
    }
}
