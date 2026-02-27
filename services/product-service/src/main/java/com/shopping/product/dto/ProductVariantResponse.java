package com.shopping.product.dto;

import java.io.Serializable;
import java.util.Map;
import java.util.UUID;
import lombok.Builder;

@Builder
public record ProductVariantResponse(
    UUID id,
    String sku,
    String name,
    String size,
    String color,
    Integer priceAdjustment,
    String status,
    Map<String, Object> attributes
) implements Serializable {
}
