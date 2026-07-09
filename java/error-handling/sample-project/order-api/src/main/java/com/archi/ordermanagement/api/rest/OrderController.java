package com.archi.ordermanagement.api.rest;

import com.archi.ordermanagement.api.rest.dto.CreateOrderRequest;
import com.archi.ordermanagement.api.rest.dto.OrderResponse;
import com.archi.ordermanagement.api.rest.dto.PageResponse;
import com.archi.ordermanagement.api.rest.mapper.OrderDtoMapper;
import com.archi.ordermanagement.core.application.port.in.CancelOrderUseCase;
import com.archi.ordermanagement.core.application.port.in.CreateOrderUseCase;
import com.archi.ordermanagement.core.application.port.in.DeleteOrderUseCase;
import com.archi.ordermanagement.core.application.port.in.GetOrderUseCase;
import com.archi.ordermanagement.core.application.port.in.ListOrdersUseCase;
import com.archi.ordermanagement.core.domain.Order;
import com.archi.ordermanagement.core.domain.paging.PageRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Five single-method use case interfaces are injected instead of one facade interface: each
 * models exactly one business operation (ISP), and a controller is the composition root where
 * that fan-out is expected to surface. This intentionally exceeds the "max 3-4 parameters"
 * guideline, whose intent is to avoid unreadable primitive-heavy signatures, not to discourage
 * named, typed, single-purpose collaborators.
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final CreateOrderUseCase createOrderUseCase;
    private final GetOrderUseCase getOrderUseCase;
    private final ListOrdersUseCase listOrdersUseCase;
    private final CancelOrderUseCase cancelOrderUseCase;
    private final DeleteOrderUseCase deleteOrderUseCase;

    public OrderController(CreateOrderUseCase createOrderUseCase,
                            GetOrderUseCase getOrderUseCase,
                            ListOrdersUseCase listOrdersUseCase,
                            CancelOrderUseCase cancelOrderUseCase,
                            DeleteOrderUseCase deleteOrderUseCase) {
        this.createOrderUseCase = createOrderUseCase;
        this.getOrderUseCase = getOrderUseCase;
        this.listOrdersUseCase = listOrdersUseCase;
        this.cancelOrderUseCase = cancelOrderUseCase;
        this.deleteOrderUseCase = deleteOrderUseCase;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> create(@Valid @RequestBody CreateOrderRequest request) {
        Order order = createOrderUseCase.createOrder(OrderDtoMapper.toCommand(request));
        return ResponseEntity.status(201).body(OrderDtoMapper.toResponse(order));
    }

    @GetMapping("/{id}")
    public OrderResponse get(@PathVariable UUID id) {
        return OrderDtoMapper.toResponse(getOrderUseCase.getOrder(id));
    }

    @GetMapping
    public PageResponse<OrderResponse> list(@RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "20") int size) {
        return OrderDtoMapper.toPageResponse(listOrdersUseCase.listOrders(new PageRequest(page, size)));
    }

    @PostMapping("/{id}/cancel")
    public OrderResponse cancel(@PathVariable UUID id) {
        return OrderDtoMapper.toResponse(cancelOrderUseCase.cancelOrder(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        deleteOrderUseCase.deleteOrder(id);
        return ResponseEntity.noContent().build();
    }
}
