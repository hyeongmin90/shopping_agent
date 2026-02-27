package com.shopping.order.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopping.order.entity.OutboxEvent;
import com.shopping.order.repository.OutboxEventRepository;
import com.shopping.order.support.EventEnvelope;
import java.time.Instant;
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
            UUID aggregateId,
            String eventType,
            Map<String, Object> data,
            UUID correlationId,
            UUID causationId,
            String idempotencyKey
    ) {
        EventEnvelope<Map<String, Object>> envelope = EventEnvelope.<Map<String, Object>>builder()
                .meta(EventEnvelope.EventMeta.builder()
                        .eventId(UUID.randomUUID())
                        .eventType(eventType)
                        .schemaVersion(1)
                        .occurredAt(Instant.now())
                        .producer("order-service")
                        .correlationId(correlationId)
                        .causationId(causationId)
                        .idempotencyKey(idempotencyKey)
                        .build())
                .data(data)
                .build();

        OutboxEvent outboxEvent = new OutboxEvent();
        outboxEvent.setAggregateType(aggregateType);
        outboxEvent.setAggregateId(aggregateId);
        outboxEvent.setEventType(eventType);
        outboxEvent.setPayload(write(envelope));
        outboxEvent.setCorrelationId(correlationId);
        outboxEvent.setCausationId(causationId);
        outboxEvent.setIdempotencyKey(idempotencyKey);
        outboxEventRepository.save(outboxEvent);
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize outbox payload", e);
        }
    }
}
