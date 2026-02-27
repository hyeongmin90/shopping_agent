package com.shopping.inventory.service;

import com.shopping.inventory.entity.InventoryReservation;
import com.shopping.inventory.kafka.InventoryEventPublisher;
import com.shopping.inventory.kafka.model.CancelInventoryReservationCommand;
import com.shopping.inventory.kafka.model.CommandMeta;
import com.shopping.inventory.kafka.model.CommitInventoryCommand;
import com.shopping.inventory.kafka.model.ReserveInventoryCommand;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryCommandService {

    private final InventoryMutationService inventoryMutationService;
    private final InventoryEventPublisher eventPublisher;

    public void handleReserve(ReserveInventoryCommand command, CommandMeta meta, int ttlMinutes) {
        UUID orderId = command.getOrderId();
        try {
            InventoryReservation reservation = inventoryMutationService.reserve(command, ttlMinutes);
            eventPublisher.publish(
                    "InventoryReserved",
                    safeCorrelationId(meta, orderId),
                    meta.getEventId(),
                    meta.getIdempotencyKey(),
                    reservationData(reservation)
            );
        } catch (Exception e) {
            log.warn("Inventory reservation failed for order {}: {}", orderId, e.getMessage());
            Map<String, Object> failure = new HashMap<>();
            failure.put("orderId", orderId);
            failure.put("reason", e.getMessage());
            eventPublisher.publish(
                    "InventoryReservationFailed",
                    safeCorrelationId(meta, orderId),
                    meta.getEventId(),
                    meta.getIdempotencyKey(),
                    failure
            );
        }
    }

    public void handleCommit(CommitInventoryCommand command, CommandMeta meta) {
        InventoryReservation reservation = inventoryMutationService.commit(command);

        eventPublisher.publish(
                "InventoryCommitted",
                safeCorrelationId(meta, reservation.getOrderId()),
                meta.getEventId(),
                meta.getIdempotencyKey(),
                reservationData(reservation)
        );
    }

    public void handleCancel(CancelInventoryReservationCommand command, CommandMeta meta) {
        InventoryReservation reservation = inventoryMutationService.cancel(command);

        eventPublisher.publish(
                "InventoryReservationCancelled",
                safeCorrelationId(meta, reservation.getOrderId()),
                meta.getEventId(),
                meta.getIdempotencyKey(),
                cancellationData(reservation, command.getReason())
        );
    }

    public void expireReservations(LocalDateTime now) {
        for (InventoryReservation reservation : inventoryMutationService.expireReservations(now)) {
            eventPublisher.publish(
                    "InventoryReservationCancelled",
                    reservation.getOrderId().toString(),
                    null,
                    null,
                    cancellationData(reservation, "EXPIRED")
            );
        }
    }

    private String safeCorrelationId(CommandMeta meta, UUID fallbackOrderId) {
        if (meta.getCorrelationId() != null) {
            return meta.getCorrelationId();
        }
        return fallbackOrderId != null ? fallbackOrderId.toString() : null;
    }

    private Map<String, Object> reservationData(InventoryReservation reservation) {
        Map<String, Object> data = new HashMap<>();
        data.put("reservationId", reservation.getId());
        data.put("orderId", reservation.getOrderId());
        data.put("status", reservation.getStatus().name());
        data.put("expiresAt", reservation.getExpiresAt());
        data.put("items", reservation.getItems().stream().map(item -> Map.of(
                "inventoryId", item.getInventory().getId(),
                "productId", item.getInventory().getProductId(),
                "variantId", item.getInventory().getVariantId(),
                "sku", item.getInventory().getSku(),
                "quantity", item.getQuantity()
        )).toList());
        return data;
    }

    private Map<String, Object> cancellationData(InventoryReservation reservation, String reason) {
        Map<String, Object> data = new HashMap<>();
        data.put("reservationId", reservation.getId());
        data.put("orderId", reservation.getOrderId());
        data.put("status", reservation.getStatus().name());
        data.put("reason", reason);
        return data;
    }
}
