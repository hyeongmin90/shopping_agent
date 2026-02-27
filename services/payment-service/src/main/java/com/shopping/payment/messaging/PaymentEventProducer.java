package com.shopping.payment.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopping.payment.messaging.model.EventEnvelope;
import com.shopping.payment.messaging.model.EventMeta;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${payment.kafka.events-topic:payment.events}")
    private String eventsTopic;

    public void publishEvent(String eventType, Object eventData, EventMeta sourceMeta) {
        EventMeta meta = EventMeta.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(eventType)
                .schemaVersion(1)
                .occurredAt(Instant.now().toString())
                .producer("payment-service")
                .correlationId(resolveCorrelationId(sourceMeta))
                .causationId(sourceMeta == null ? null : sourceMeta.getEventId())
                .idempotencyKey(sourceMeta == null ? null : sourceMeta.getIdempotencyKey())
                .build();

        EventEnvelope<Object> envelope = EventEnvelope.builder()
                .meta(meta)
                .data(eventData)
                .build();

        try {
            String payload = objectMapper.writeValueAsString(envelope);
            kafkaTemplate.send(eventsTopic, meta.getCorrelationId(), payload);
            log.info("Published {} to topic {} with eventId={}", eventType, eventsTopic, meta.getEventId());
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize event envelope", exception);
        }
    }

    private String resolveCorrelationId(EventMeta sourceMeta) {
        if (sourceMeta == null) {
            return UUID.randomUUID().toString();
        }
        if (sourceMeta.getCorrelationId() != null && !sourceMeta.getCorrelationId().isBlank()) {
            return sourceMeta.getCorrelationId();
        }
        if (sourceMeta.getEventId() != null && !sourceMeta.getEventId().isBlank()) {
            return sourceMeta.getEventId();
        }
        return UUID.randomUUID().toString();
    }
}
