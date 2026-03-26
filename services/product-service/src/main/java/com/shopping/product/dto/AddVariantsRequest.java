package com.shopping.product.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;

public record AddVariantsRequest(
        @Valid @NotEmpty List<VariantRequest> variants) {

    public record VariantRequest(
            @NotBlank @Size(max = 50) String sku,
            @NotBlank @Size(max = 100) String name,
            @Size(max = 20) String size,
            @Size(max = 50) String color,
            Integer priceAdjustment,
            Map<String, Object> attributes,
            @Min(0) Integer initialStock) {

        public VariantRequest {
            if (priceAdjustment == null) {
                priceAdjustment = 0;
            }
            if (initialStock == null) {
                initialStock = 0;
            }
        }
    }
}
