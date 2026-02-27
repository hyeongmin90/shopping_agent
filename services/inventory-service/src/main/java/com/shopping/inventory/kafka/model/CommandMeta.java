package com.shopping.inventory.kafka.model;

import lombok.Data;

@Data
public class CommandMeta {
    private String eventId;
    private String eventType;
    private String schemaVersion;
    private String occurredAt;
    private String producer;
    private String correlationId;
    private String causationId;
    private String idempotencyKey;
}
