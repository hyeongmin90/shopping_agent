package com.shopping.product.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductRequest {

    @NotBlank
    @Size(max = 255)
    private String name;

    private String description;

    @Size(max = 100)
    private String brand;

    private UUID categoryId;

    @NotNull
    @Min(0)
    private Integer basePrice;

    @Size(max = 3)
    @Builder.Default
    private String currency = "KRW";

    @Size(max = 500)
    private String imageUrl;

    @Min(0)
    private Integer shippingDays;

    private List<String> compatibilityTags;

    @Valid
    private List<VariantRequest> variants;

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
