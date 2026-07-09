package com.archi.ordermanagement.core.application.port.out;

import com.archi.ordermanagement.core.domain.Order;
import com.archi.ordermanagement.core.domain.paging.PageRequest;
import com.archi.ordermanagement.core.domain.paging.PageResult;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepositoryPort {

    Order save(Order order);

    Optional<Order> findById(UUID id);

    PageResult<Order> findAll(PageRequest pageRequest);

    void deleteById(UUID id);
}
