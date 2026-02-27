package com.shopping.order.dto;

import com.shopping.order.enums.OrderStatus;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class OrderStatusResponse {
    private UUID orderId;
    private OrderStatus status;
    private String sagaStatus;
    private String message;
}
