package com.shopping.order.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import lombok.Data;

@Data
public class CartItemRequest {
    @NotNull
    private UUID productId;

    private UUID variantId;

    @Min(1)
    private Integer quantity;
}
