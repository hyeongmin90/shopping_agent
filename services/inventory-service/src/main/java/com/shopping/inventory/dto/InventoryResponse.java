package com.shopping.inventory.dto;

import java.util.UUID;

public record InventoryResponse(
        UUID inventoryId,
        UUID productId,
        UUID variantId,
        String sku,
        Integer totalQuantity,
        Integer reservedQuantity,
        Integer availableQuantity,
        Integer lowStockThreshold,
        boolean lowStock
) {
}
