package com.shopping.order.dto;

import com.shopping.order.enums.OrderStatus;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class CheckoutResponse {
    private UUID orderId;
    private OrderStatus status;
    private Integer totalAmount;
    private String currency;
}
