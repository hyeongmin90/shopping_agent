package com.shopping.product.dto;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.Builder;

@Builder
public record ProductResponse(
    UUID id,
    String name,
    String description,
    String brand,
    UUID categoryId,
    String categoryName,
    Integer basePrice,
    String currency,
    String imageUrl,
    String status,
    Integer shippingDays,
    List<String> compatibilityTags,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    List<ProductVariantResponse> variants
) implements Serializable {
}
