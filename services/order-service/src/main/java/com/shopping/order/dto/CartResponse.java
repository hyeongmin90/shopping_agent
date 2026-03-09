package com.shopping.order.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CartResponse {
    private UUID id;
    private UUID userId;
    private String currency;
    private Integer totalAmount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<CartItemView> items;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CartItemView {
        private UUID id;
        private UUID productId;
        private UUID variantId;
        private String productName;
        private Integer quantity;
        private Integer unitPrice;
    }
}
