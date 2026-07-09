package com.archi.ordermanagement.api.rest.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record CreateOrderRequest(

        @NotBlank
        String customerId,

        @NotNull
        @DecimalMin(value = "0.01")
        BigDecimal amount
) {
}
