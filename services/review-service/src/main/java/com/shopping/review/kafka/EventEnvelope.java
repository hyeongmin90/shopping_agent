package com.shopping.review.kafka;

import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class EventEnvelope<T> {
    EventMeta meta;
    T data;

    @Value
    @Builder
    public static class EventMeta {
        UUID eventId;
        String eventType;
        Instant occurredAt;
        String correlationId;
    }
}
