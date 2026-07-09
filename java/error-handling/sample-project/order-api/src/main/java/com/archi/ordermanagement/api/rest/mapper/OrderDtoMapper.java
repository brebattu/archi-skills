package com.archi.ordermanagement.api.rest.mapper;

import com.archi.ordermanagement.api.rest.dto.CreateOrderRequest;
import com.archi.ordermanagement.api.rest.dto.OrderResponse;
import com.archi.ordermanagement.api.rest.dto.PageResponse;
import com.archi.ordermanagement.core.application.port.in.CreateOrderCommand;
import com.archi.ordermanagement.core.domain.Order;
import com.archi.ordermanagement.core.domain.paging.PageResult;

/** No Spring dependency needed for a stateless mapping utility. */
public final class OrderDtoMapper {

    private OrderDtoMapper() {
    }

    public static CreateOrderCommand toCommand(CreateOrderRequest request) {
        return new CreateOrderCommand(request.customerId(), request.amount());
    }

    public static OrderResponse toResponse(Order order) {
        return new OrderResponse(order.id(), order.customerId(), order.amount(),
                order.status().name(), order.createdAt());
    }

    public static PageResponse<OrderResponse> toPageResponse(PageResult<Order> pageResult) {
        return new PageResponse<>(
                pageResult.content().stream().map(OrderDtoMapper::toResponse).toList(),
                pageResult.page(),
                pageResult.size(),
                pageResult.totalElements());
    }
}
