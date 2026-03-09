package com.shopping.product.dto;

import java.util.UUID;
import lombok.Builder;

@Builder
public record ProductSearchRequest(
    String category,
    String brand,
    Integer minPrice,
    Integer maxPrice,
    String keyword
) {

    public String cacheKey() {
        return String.join(
            "|",
            normalize(category),
            normalize(brand),
            minPrice == null ? "" : minPrice.toString(),
            maxPrice == null ? "" : maxPrice.toString(),
            normalize(keyword)
        );
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }
}
