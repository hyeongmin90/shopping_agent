package com.shopping.payment.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopping.payment.domain.OutboxEvent;
import com.shopping.payment.messaging.model.EventEnvelope;
import com.shopping.payment.messaging.model.EventMeta;
import com.shopping.payment.repository.OutboxEventRepository;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import java.time.Instant;
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
            Object eventData,
            String correlationId,
            String causationId,
            String idempotencyKey
    ) {
        EventMeta meta = EventMeta.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(eventType)
                .schemaVersion(1)
                .occurredAt(Instant.now().toString())
                .producer("payment-service")
                .correlationId(correlationId)
                .causationId(causationId)
                .idempotencyKey(idempotencyKey)
                .build();

        EventEnvelope<Object> envelope = EventEnvelope.builder()
                .meta(meta)
                .data(eventData)
                .build();

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
