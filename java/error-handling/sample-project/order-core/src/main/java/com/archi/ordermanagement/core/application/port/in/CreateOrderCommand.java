package com.archi.ordermanagement.core.application.port.in;

import java.math.BigDecimal;

public record CreateOrderCommand(String customerId, BigDecimal amount) {
}
