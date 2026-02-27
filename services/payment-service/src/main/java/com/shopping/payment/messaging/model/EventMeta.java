package com.shopping.payment.messaging.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EventMeta {
    private String eventId;
    private String eventType;
    private Integer schemaVersion;
    private String occurredAt;
    private String producer;
    private String correlationId;
    private String causationId;
    private String idempotencyKey;
}
