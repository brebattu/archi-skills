package com.archi.ordermanagement.core.application.port.in;

import com.archi.ordermanagement.core.domain.Order;
import com.archi.ordermanagement.core.domain.paging.PageRequest;
import com.archi.ordermanagement.core.domain.paging.PageResult;

public interface ListOrdersUseCase {

    PageResult<Order> listOrders(PageRequest pageRequest);
}
