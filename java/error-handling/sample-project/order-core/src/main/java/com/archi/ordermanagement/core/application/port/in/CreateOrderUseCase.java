package com.archi.ordermanagement.core.application.port.in;

import com.archi.ordermanagement.core.domain.Order;

public interface CreateOrderUseCase {

    Order createOrder(CreateOrderCommand command);
}
