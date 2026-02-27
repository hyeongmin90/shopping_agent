package com.shopping.product.kafka;

import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Builder;

@Builder
public record ProductViewedEvent(
    UUID eventId,
    UUID productId,
    String productName,
    UUID categoryId,
    String brand,
    OffsetDateTime viewedAt,
    String correlationId
) {
}
