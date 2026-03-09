package com.shopping.order.client;

import java.util.UUID;
import lombok.Data;

@Data
public class ProductDto {
    private UUID id;
    private String name;
    private String description;
    private Integer basePrice;
    private String currency;
    private String category;
    private String brand;
}
