package com.shopping.inventory.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopping.inventory.exception.InvalidCommandException;
import com.shopping.inventory.kafka.model.CancelInventoryReservationCommand;
import com.shopping.inventory.kafka.model.CommandEnvelope;
import com.shopping.inventory.kafka.model.CommitInventoryCommand;
import com.shopping.inventory.kafka.model.ReserveInventoryCommand;
import com.shopping.inventory.service.IdempotencyService;
import com.shopping.inventory.service.InventoryCommandService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class InventoryCommandConsumer {

    private static final String CONSUMER_ID = "inventory-service";

    private final ObjectMapper objectMapper;
    private final InventoryCommandService inventoryCommandService;
    private final IdempotencyService idempotencyService;

    @Value("${app.reservation.ttl-minutes}")
    private int reservationTtlMinutes;

    @KafkaListener(topics = "${app.kafka.topics.commands}")
    @Transactional
    public void onMessage(String payload) {
        CommandEnvelope envelope = parseEnvelope(payload);
        UUID eventId = parseEventId(envelope);

        if (idempotencyService.isProcessed(CONSUMER_ID, eventId)) {
            log.info("Skipping duplicated inventory command eventId={}", eventId);
            return;
        }

        String eventType = envelope.getMeta().getEventType();
        switch (eventType) {
            case "ReserveInventoryCommand" -> {
                ReserveInventoryCommand command = objectMapper.convertValue(envelope.getData(), ReserveInventoryCommand.class);
                inventoryCommandService.handleReserve(command, envelope.getMeta(), reservationTtlMinutes);
            }
            case "CommitInventoryCommand" -> {
                CommitInventoryCommand command = objectMapper.convertValue(envelope.getData(), CommitInventoryCommand.class);
                inventoryCommandService.handleCommit(command, envelope.getMeta());
            }
            case "CancelInventoryReservationCommand" -> {
                CancelInventoryReservationCommand command = objectMapper.convertValue(
                        envelope.getData(), CancelInventoryReservationCommand.class);
                inventoryCommandService.handleCancel(command, envelope.getMeta());
            }
            default -> throw new InvalidCommandException("Unsupported inventory command eventType: " + eventType);
        }

        idempotencyService.markProcessed(CONSUMER_ID, eventId);
    }

    private CommandEnvelope parseEnvelope(String payload) {
        try {
            return objectMapper.readValue(payload, CommandEnvelope.class);
        } catch (JsonProcessingException e) {
            throw new InvalidCommandException("Failed to parse inventory command payload");
        }
    }

    private UUID parseEventId(CommandEnvelope envelope) {
        try {
            return UUID.fromString(envelope.getMeta().getEventId());
        } catch (Exception e) {
            throw new InvalidCommandException("Invalid or missing eventId in command envelope meta");
        }
    }
}
