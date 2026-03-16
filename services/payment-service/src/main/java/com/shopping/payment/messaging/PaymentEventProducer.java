package com.shopping.payment.messaging;

import com.shopping.payment.messaging.model.EventMeta;
import com.shopping.payment.service.OutboxService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventProducer {

    private final OutboxService outboxService;

    public void publishEvent(String eventType, Object eventData, EventMeta sourceMeta) {
        String correlationId = resolveCorrelationId(sourceMeta);
        String causationId = sourceMeta == null ? null : sourceMeta.getEventId();
        String idempotencyKey = sourceMeta == null ? null : sourceMeta.getIdempotencyKey();

        outboxService.enqueue(
                "Payment",
                correlationId,
                eventType,
                eventData,
                correlationId,
                causationId,
                idempotencyKey
        );

        log.info("Enqueued {} to outbox with correlationId={}", eventType, correlationId);
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
