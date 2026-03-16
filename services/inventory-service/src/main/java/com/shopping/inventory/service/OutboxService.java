package com.shopping.inventory.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopping.inventory.entity.OutboxEvent;
import com.shopping.inventory.repository.OutboxEventRepository;
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
        outboxEventRepository.save(outboxEvent);
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize outbox payload", e);
        }
    }
}
