package com.shopping.order.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopping.order.dto.ApproveOrderRequest;
import com.shopping.order.dto.OrderResponse;
import com.shopping.order.dto.OrderStatusResponse;
import com.shopping.order.enums.OrderSagaStatus;
import com.shopping.order.enums.OrderStatus;
import com.shopping.order.service.OrderService;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private OrderService orderService;

    @Test
    @DisplayName("주문 단건 조회 API")
    void getOrder_Api() throws Exception {
        // given
        UUID orderId = UUID.randomUUID();
        OrderResponse response = OrderResponse.builder()
                .id(orderId)
                .status(OrderStatus.PENDING_APPROVAL)
                .totalAmount(10000)
                .build();

        when(orderService.getOrder(orderId)).thenReturn(response);

        // when & then
        mockMvc.perform(get("/api/orders/{id}", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId.toString()))
                .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"));
    }

    @Test
    @DisplayName("주문 승인 API")
    void approve_Api() throws Exception {
        // given
        UUID orderId = UUID.randomUUID();
        ApproveOrderRequest request = new ApproveOrderRequest();
        request.setPaymentMethod("CARD");

        OrderStatusResponse response = new OrderStatusResponse(orderId, OrderStatus.INVENTORY_RESERVING,
                OrderSagaStatus.RUNNING.name(), "OK");

        when(orderService.approve(eq(orderId), any(ApproveOrderRequest.class))).thenReturn(response);

        // when & then
        mockMvc.perform(post("/api/orders/{id}/approve", orderId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.status").value("INVENTORY_RESERVING"));
    }

    @Test
    @DisplayName("주문 취소 API")
    void cancel_Api() throws Exception {
        // given
        UUID orderId = UUID.randomUUID();
        OrderStatusResponse response = new OrderStatusResponse(orderId, OrderStatus.CANCELLED,
                OrderSagaStatus.COMPENSATING.name(), "Order cancelled");

        when(orderService.cancel(eq(orderId), any())).thenReturn(response);

        // when & then
        mockMvc.perform(post("/api/orders/{id}/cancel", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }
}
