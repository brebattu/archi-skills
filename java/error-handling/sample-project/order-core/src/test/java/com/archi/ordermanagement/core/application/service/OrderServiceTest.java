package com.archi.ordermanagement.core.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.archi.errorhandling.FunctionalException;
import com.archi.ordermanagement.core.application.port.in.CreateOrderCommand;
import com.archi.ordermanagement.core.application.port.out.OrderEventPublisherPort;
import com.archi.ordermanagement.core.application.port.out.OrderRepositoryPort;
import com.archi.ordermanagement.core.domain.Order;
import com.archi.ordermanagement.core.domain.error.OrderErrorCode;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepositoryPort orderRepository;

    @Mock
    private OrderEventPublisherPort orderEventPublisher;

    @Test
    void should_createOrder_and_publishEvent_when_commandIsValid() {
        OrderService service = new OrderService(orderRepository, orderEventPublisher);
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Order created = service.createOrder(new CreateOrderCommand("customer-1", BigDecimal.TEN));

        assertEquals("customer-1", created.customerId());
        verify(orderEventPublisher).publishOrderCreated(created);
    }

    @Test
    void should_throwFunctionalException_when_cancellingUnknownOrder() {
        OrderService service = new OrderService(orderRepository, orderEventPublisher);
        UUID unknownId = UUID.randomUUID();
        when(orderRepository.findById(unknownId)).thenReturn(Optional.empty());

        FunctionalException exception =
                assertThrows(FunctionalException.class, () -> service.cancelOrder(unknownId));
        assertEquals(OrderErrorCode.ORDER_NOT_FOUND, exception.errorCode());
    }
}
