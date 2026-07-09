package com.archi.ordermanagement.persistence.adapter;

import com.archi.errorhandling.TechnicalException;
import com.archi.ordermanagement.core.application.port.out.OrderRepositoryPort;
import com.archi.ordermanagement.core.domain.Order;
import com.archi.ordermanagement.core.domain.paging.PageResult;
import com.archi.ordermanagement.persistence.error.OrderPersistenceErrorCode;
import com.archi.ordermanagement.persistence.jpa.OrderEntity;
import com.archi.ordermanagement.persistence.jpa.OrderJpaRepository;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

/**
 * Only the Spring-dependent adapter modules use component-scan ({@code @Component}); order-core
 * itself is wired manually since it has no Spring dependency at all.
 *
 * <p>Note: this file is the one boundary where core's {@link
 * com.archi.ordermanagement.core.domain.paging.PageRequest} and Spring Data's {@link
 * org.springframework.data.domain.PageRequest} both appear; the latter is fully qualified below
 * to avoid a naming collision.
 *
 * <p>This adapter owns the translation of its own technical failures: a raw {@link
 * DataAccessException} must never reach the API layer, or it would be mistaken for a bug by the
 * generic catch-all handler instead of a known, retryable infrastructure failure.
 */
@Component
public class OrderRepositoryAdapter implements OrderRepositoryPort {

    private final OrderJpaRepository orderJpaRepository;

    public OrderRepositoryAdapter(OrderJpaRepository orderJpaRepository) {
        this.orderJpaRepository = orderJpaRepository;
    }

    @Override
    public Order save(Order order) {
        return executeOrThrowTechnical(() -> toDomain(orderJpaRepository.save(toEntity(order))));
    }

    @Override
    public Optional<Order> findById(UUID id) {
        return executeOrThrowTechnical(() -> orderJpaRepository.findById(id).map(OrderRepositoryAdapter::toDomain));
    }

    @Override
    public PageResult<Order> findAll(com.archi.ordermanagement.core.domain.paging.PageRequest pageRequest) {
        return executeOrThrowTechnical(() -> {
            org.springframework.data.domain.PageRequest springPageRequest =
                    org.springframework.data.domain.PageRequest.of(pageRequest.page(), pageRequest.size());
            Page<OrderEntity> page = orderJpaRepository.findAll(springPageRequest);
            return PageResult.of(page.getContent().stream().map(OrderRepositoryAdapter::toDomain).toList(),
                    page.getNumber(), page.getSize(), page.getTotalElements());
        });
    }

    @Override
    public void deleteById(UUID id) {
        executeOrThrowTechnical(() -> {
            orderJpaRepository.deleteById(id);
            return null;
        });
    }

    private static <T> T executeOrThrowTechnical(Supplier<T> operation) {
        try {
            return operation.get();
        } catch (DataAccessException e) {
            throw new TechnicalException(OrderPersistenceErrorCode.ORDER_PERSISTENCE_FAILURE,
                    "Order persistence operation failed", e);
        }
    }

    private static Order toDomain(OrderEntity entity) {
        return Order.reconstruct(entity.getId(), entity.getCustomerId(), entity.getAmount(),
                entity.getStatus(), entity.getCreatedAt());
    }

    private static OrderEntity toEntity(Order order) {
        return new OrderEntity(order.id(), order.customerId(), order.amount(), order.status(), order.createdAt());
    }
}
