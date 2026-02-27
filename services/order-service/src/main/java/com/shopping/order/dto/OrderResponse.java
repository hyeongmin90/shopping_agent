package com.shopping.order.dto;

import com.shopping.order.enums.OrderStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class OrderResponse {
    private UUID id;
    private UUID userId;
    private OrderStatus status;
    private Integer totalAmount;
    private String currency;
    private String shippingAddress;
    private String failureReason;
    private List<OrderItemView> items;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Getter
    @Builder
    public static class OrderItemView {
        private UUID id;
        private UUID productId;
        private UUID variantId;
        private String productName;
        private Integer quantity;
        private Integer unitPrice;
    }
}
