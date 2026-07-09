package com.archi.ordermanagement.core.application.port.in;

import com.archi.errorhandling.FunctionalException;
import com.archi.ordermanagement.core.domain.Order;
import java.util.UUID;

public interface CancelOrderUseCase {

    /**
     * @throws FunctionalException with {@code OrderErrorCode.ORDER_NOT_FOUND} if no order exists
     *                              for {@code orderId}, or {@code OrderErrorCode.ORDER_ALREADY_CANCELLED}
     *                              if the order cannot transition to CANCELLED.
     */
    Order cancelOrder(UUID orderId);
}
