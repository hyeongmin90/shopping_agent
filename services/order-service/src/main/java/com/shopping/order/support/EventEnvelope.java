package com.shopping.order.support;

import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class EventEnvelope<T> {
    private EventMeta meta;
    private T data;

    @Getter
    @Builder
    public static class EventMeta {
        private UUID eventId;
        private String eventType;
        private Integer schemaVersion;
        private Instant occurredAt;
        private String producer;
        private UUID correlationId;
        private UUID causationId;
        private String idempotencyKey;
        private String traceparent;
    }
}
