package com.shopping.product.dto;

import java.io.Serializable;
import java.util.List;
import java.util.UUID;
import lombok.Builder;

@Builder
public record CategoryResponse(
    UUID id,
    String name,
    UUID parentId,
    String description,
    List<CategoryResponse> children
) implements Serializable {
}
