package com.shopping.product.kafka;

import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Builder;

@Builder
public record ProductIndexEvent(
    UUID eventId,
    String eventType,
    UUID productId,
    String name,
    String description,
    String brand,
    String categoryName,
    Integer basePrice,
    String currency,
    String status,
    OffsetDateTime occurredAt,
    String correlationId
) {
}
