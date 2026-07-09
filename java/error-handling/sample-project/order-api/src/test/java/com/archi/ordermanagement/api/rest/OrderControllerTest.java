package com.archi.ordermanagement.api.rest;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.archi.errorhandling.ErrorCode;
import com.archi.errorhandling.FunctionalException;
import com.archi.errorhandling.TechnicalException;
import com.archi.ordermanagement.api.rest.dto.CreateOrderRequest;
import com.archi.ordermanagement.core.application.port.in.CancelOrderUseCase;
import com.archi.ordermanagement.core.application.port.in.CreateOrderUseCase;
import com.archi.ordermanagement.core.application.port.in.DeleteOrderUseCase;
import com.archi.ordermanagement.core.application.port.in.GetOrderUseCase;
import com.archi.ordermanagement.core.application.port.in.ListOrdersUseCase;
import com.archi.ordermanagement.core.domain.error.OrderErrorCode;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

/**
 * Slice test proving GlobalExceptionHandler's actual HTTP behavior, not just its compile-time
 * exhaustiveness. @WebMvcTest auto-loads @RestControllerAdvice classes alongside the controller.
 */
@WebMvcTest(OrderController.class)
class OrderControllerTest {

    // A local, unrelated ErrorCode: proves the TechnicalException handler doesn't need to know
    // about any specific enum type, unlike the FunctionalException handler's switch.
    private enum TestTechnicalErrorCode implements ErrorCode {
        SIMULATED_INFRA_FAILURE;

        @Override
        public String code() {
            return name();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CreateOrderUseCase createOrderUseCase;
    @MockitoBean
    private GetOrderUseCase getOrderUseCase;
    @MockitoBean
    private ListOrdersUseCase listOrdersUseCase;
    @MockitoBean
    private CancelOrderUseCase cancelOrderUseCase;
    @MockitoBean
    private DeleteOrderUseCase deleteOrderUseCase;

    @Test
    void should_return404WithErrorCode_when_orderNotFound() throws Exception {
        UUID orderId = UUID.randomUUID();
        when(getOrderUseCase.getOrder(orderId))
                .thenThrow(new FunctionalException(OrderErrorCode.ORDER_NOT_FOUND, "Order not found: " + orderId));

        mockMvc.perform(get("/api/orders/{id}", orderId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"))
                .andExpect(jsonPath("$.detail").value("Order not found: " + orderId));
    }

    @Test
    void should_return409_when_orderAlreadyCancelled() throws Exception {
        UUID orderId = UUID.randomUUID();
        when(cancelOrderUseCase.cancelOrder(orderId))
                .thenThrow(new FunctionalException(OrderErrorCode.ORDER_ALREADY_CANCELLED, "Order already cancelled"));

        mockMvc.perform(post("/api/orders/{id}/cancel", orderId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_ALREADY_CANCELLED"));
    }

    @Test
    void should_return400WithFieldErrors_when_createRequestInvalid() throws Exception {
        String invalidBody = objectMapper.writeValueAsString(new CreateOrderRequest("", null));

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").isArray());
    }

    @Test
    void should_return500WithoutLeakingDetail_when_technicalExceptionThrown() throws Exception {
        UUID orderId = UUID.randomUUID();
        when(getOrderUseCase.getOrder(orderId))
                .thenThrow(new TechnicalException(TestTechnicalErrorCode.SIMULATED_INFRA_FAILURE,
                        "database connection pool exhausted"));

        mockMvc.perform(get("/api/orders/{id}", orderId))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value("Technical error"))
                .andExpect(jsonPath("$.code").value("SIMULATED_INFRA_FAILURE"))
                .andExpect(jsonPath("$.incidentId").exists());
    }

    @Test
    void should_return500Generic_when_unexpectedBugOccurs() throws Exception {
        UUID orderId = UUID.randomUUID();
        when(getOrderUseCase.getOrder(orderId)).thenThrow(new NullPointerException("boom"));

        mockMvc.perform(get("/api/orders/{id}", orderId))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value("Unexpected error"))
                // no "code" or "incidentId": this path never goes through ApplicationException.
                .andExpect(jsonPath("$.code").doesNotExist());
    }
}
