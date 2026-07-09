package com.archi.ordermanagement.core.application.port.out;

import com.archi.ordermanagement.core.domain.Order;

public interface OrderEventPublisherPort {

    void publishOrderCreated(Order order);
}
