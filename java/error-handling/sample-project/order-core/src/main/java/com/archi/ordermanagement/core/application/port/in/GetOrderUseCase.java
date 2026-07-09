package com.archi.ordermanagement.core.application.port.in;

import com.archi.errorhandling.FunctionalException;
import com.archi.ordermanagement.core.domain.Order;
import java.util.UUID;

public interface GetOrderUseCase {

    /**
     * @throws FunctionalException with {@code OrderErrorCode.ORDER_NOT_FOUND} if no order exists
     *                              for {@code orderId}.
     */
    Order getOrder(UUID orderId);
}
