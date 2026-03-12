package com.shopping.product.dto;

import lombok.Builder;

@Builder
public record ProductSearchRequest(
        String category,
        String brand,
        Integer minPrice,
        Integer maxPrice,
        String keyword) {

    public String cacheKey() {
        return "cat=" + normalize(category) +
               "|brnd=" + normalize(brand) +
               "|minP=" + (minPrice == null ? "" : minPrice) +
               "|maxP=" + (maxPrice == null ? "" : maxPrice) +
               "|kwd=" + normalize(keyword);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }
}
