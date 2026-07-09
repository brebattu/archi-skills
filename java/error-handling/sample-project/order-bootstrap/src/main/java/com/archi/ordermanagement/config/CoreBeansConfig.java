package com.archi.ordermanagement.config;

import com.archi.ordermanagement.core.application.port.out.OrderEventPublisherPort;
import com.archi.ordermanagement.core.application.port.out.OrderRepositoryPort;
import com.archi.ordermanagement.core.application.service.OrderService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * order-core has zero Spring dependency, so its beans cannot be discovered by component-scan:
 * they are wired here explicitly instead. Declaring the bean's return type as the concrete
 * OrderService class (not one of the five use-case interfaces it implements) lets Spring satisfy
 * injection points typed to any of those interfaces from this single bean.
 */
@Configuration
public class CoreBeansConfig {

    @Bean
    public OrderService orderService(OrderRepositoryPort orderRepositoryPort,
                                      OrderEventPublisherPort orderEventPublisherPort) {
        return new OrderService(orderRepositoryPort, orderEventPublisherPort);
    }
}
