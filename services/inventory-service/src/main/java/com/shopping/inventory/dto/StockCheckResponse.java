package com.shopping.inventory.dto;

import java.util.UUID;

public record StockCheckResponse(
        UUID productId,
        UUID variantId,
        Integer requestedQuantity,
        Integer availableQuantity,
        boolean available,
        String message
) {
}
