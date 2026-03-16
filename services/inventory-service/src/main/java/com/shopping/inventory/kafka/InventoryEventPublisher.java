package com.shopping.inventory.kafka;

import com.shopping.inventory.service.OutboxService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InventoryEventPublisher {

    private final OutboxService outboxService;

    public void publish(
            String eventType,
            String correlationId,
            String causationId,
            String idempotencyKey,
            Object data
    ) {
        outboxService.enqueue(
                "Inventory",
                correlationId,
                eventType,
                data,
                correlationId,
                causationId,
                idempotencyKey
        );
    }
}
