package com.shopping.inventory.kafka.model;

import jakarta.validation.constraints.Min;
import java.util.UUID;
import lombok.Data;

@Data
public class ReserveInventoryItemCommand {
    private UUID productId;
    private UUID variantId;
    private String sku;

    @Min(1)
    private Integer quantity;
}
