package com.shopping.product.dto;

import java.time.OffsetDateTime;
import lombok.Builder;

@Builder
public record ErrorResponse(
    OffsetDateTime timestamp,
    int status,
    String error,
    String message,
    String path,
    String correlationId
) {
}
