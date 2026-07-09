package com.archi.ordermanagement.core.application.port.in;

import com.archi.errorhandling.FunctionalException;
import java.util.UUID;

public interface DeleteOrderUseCase {

    /**
     * @throws FunctionalException with {@code OrderErrorCode.ORDER_NOT_FOUND} if no order exists
     *                              for {@code orderId}.
     */
    void deleteOrder(UUID orderId);
}
