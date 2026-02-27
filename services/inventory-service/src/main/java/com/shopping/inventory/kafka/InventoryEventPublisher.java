package com.shopping.inventory.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopping.inventory.exception.InvalidCommandException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InventoryEventPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.kafka.topics.events}")
    private String eventsTopic;

    public void publish(
            String eventType,
            String correlationId,
            String causationId,
            String idempotencyKey,
            Object data
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

        try {
            String payload = objectMapper.writeValueAsString(envelope);
            kafkaTemplate.send(eventsTopic, correlationId, payload);
        } catch (JsonProcessingException e) {
            throw new InvalidCommandException("Failed to serialize inventory event: " + e.getMessage());
        }
    }
}
