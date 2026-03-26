package com.shopping.inventory.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopping.inventory.entity.OutboxEvent;
import com.shopping.inventory.repository.OutboxEventRepository;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OutboxService {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    private final Tracer tracer;

    public void enqueue(
            String aggregateType,
            String aggregateId,
            String eventType,
            Object data,
            String correlationId,
            String causationId,
            String idempotencyKey
    ) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        Map<String, Object> meta = new LinkedHashMap<>();

        meta.put("eventId", UUID.randomUUID().toString());
        meta.put("eventType", eventType);
        meta.put("schemaVersion", "1.0");
        meta.put("occurredAt", OffsetDateTime.now(ZoneOffset.UTC).toString());
        meta.put("producer", "inventory-service");
        meta.put("correlationId", correlationId);
        meta.put("causationId", causationId);
        meta.put("idempotencyKey", idempotencyKey);

        envelope.put("meta", meta);
        envelope.put("data", data);

        OutboxEvent outboxEvent = new OutboxEvent();
        outboxEvent.setAggregateType(aggregateType);
        outboxEvent.setAggregateId(aggregateId);
        outboxEvent.setEventType(eventType);
        outboxEvent.setPayload(serialize(envelope));
        outboxEvent.setCorrelationId(correlationId);
        outboxEvent.setCausationId(causationId);
        outboxEvent.setIdempotencyKey(idempotencyKey);
        outboxEvent.setTraceparent(currentTraceparent());
        outboxEventRepository.save(outboxEvent);
    }

    private String currentTraceparent() {
        Span span = tracer.currentSpan();
        if (span == null) return null;
        TraceContext ctx = span.context();
        String flags = Boolean.TRUE.equals(ctx.sampled()) ? "01" : "00";
        return "00-" + ctx.traceId() + "-" + ctx.spanId() + "-" + flags;
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize outbox payload", e);
        }
    }
}
