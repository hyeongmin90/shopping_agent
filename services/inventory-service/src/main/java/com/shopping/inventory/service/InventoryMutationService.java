package com.shopping.inventory.service;

import com.shopping.inventory.entity.Inventory;
import com.shopping.inventory.entity.InventoryReservation;
import com.shopping.inventory.entity.InventoryReservationItem;
import com.shopping.inventory.enums.ReservationStatus;
import com.shopping.inventory.exception.InsufficientStockException;
import com.shopping.inventory.exception.InvalidCommandException;
import com.shopping.inventory.kafka.model.CancelInventoryReservationCommand;
import com.shopping.inventory.kafka.model.CommitInventoryCommand;
import com.shopping.inventory.kafka.model.ReserveInventoryCommand;
import com.shopping.inventory.kafka.model.ReserveInventoryItemCommand;
import com.shopping.inventory.repository.InventoryRepository;
import com.shopping.inventory.repository.InventoryReservationRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InventoryMutationService {

    private static final int MAX_OPTIMISTIC_RETRIES = 5;

    private final InventoryRepository inventoryRepository;
    private final InventoryReservationRepository reservationRepository;

    @CacheEvict(cacheNames = {"inventoryByProduct", "inventoryBySku", "inventoryAvailability"}, allEntries = true)
    @Transactional
    public InventoryReservation reserve(ReserveInventoryCommand command, int ttlMinutes) {
        if (command.getOrderId() == null || command.getItems() == null || command.getItems().isEmpty()) {
            throw new InvalidCommandException("ReserveInventoryCommand requires orderId and at least one item");
        }

        InventoryReservation reservation = InventoryReservation.builder()
                .orderId(command.getOrderId())
                .status(ReservationStatus.ACTIVE)
                .expiresAt(LocalDateTime.now().plusMinutes(ttlMinutes))
                .build();

        for (ReserveInventoryItemCommand commandItem : command.getItems()) {
            Inventory inventory = reserveWithOptimisticRetry(resolveInventory(commandItem), commandItem.getQuantity());
            InventoryReservationItem item = InventoryReservationItem.builder()
                    .reservation(reservation)
                    .inventory(inventory)
                    .quantity(commandItem.getQuantity())
                    .build();
            reservation.getItems().add(item);
        }

        return reservationRepository.save(reservation);
    }

    @CacheEvict(cacheNames = {"inventoryByProduct", "inventoryBySku", "inventoryAvailability"}, allEntries = true)
    @Transactional
    public InventoryReservation commit(CommitInventoryCommand command) {
        InventoryReservation reservation = findActiveReservation(command.getOrderId(), command.getReservationId());

        for (InventoryReservationItem item : reservation.getItems()) {
            commitWithOptimisticRetry(item.getInventory().getId(), item.getQuantity());
        }

        reservation.setStatus(ReservationStatus.COMMITTED);
        return reservationRepository.save(reservation);
    }

    @CacheEvict(cacheNames = {"inventoryByProduct", "inventoryBySku", "inventoryAvailability"}, allEntries = true)
    @Transactional
    public InventoryReservation cancel(CancelInventoryReservationCommand command) {
        InventoryReservation reservation = findActiveReservation(command.getOrderId(), command.getReservationId());
        cancelReservationInternal(reservation, ReservationStatus.CANCELLED);
        return reservation;
    }

    @CacheEvict(cacheNames = {"inventoryByProduct", "inventoryBySku", "inventoryAvailability"}, allEntries = true)
    @Transactional
    public List<InventoryReservation> expireReservations(LocalDateTime now) {
        List<InventoryReservation> expired = reservationRepository.findByStatusAndExpiresAtBefore(ReservationStatus.ACTIVE, now);
        for (InventoryReservation reservation : expired) {
            cancelReservationInternal(reservation, ReservationStatus.EXPIRED);
        }
        return expired;
    }

    private Inventory resolveInventory(ReserveInventoryItemCommand item) {
        if (item.getSku() != null && !item.getSku().isBlank()) {
            return inventoryRepository.findBySku(item.getSku())
                    .orElseThrow(() -> new InvalidCommandException("Inventory not found by sku: " + item.getSku()));
        }

        if (item.getProductId() == null || item.getVariantId() == null) {
            throw new InvalidCommandException("Each reserve item needs sku or productId+variantId");
        }

        return inventoryRepository.findByProductIdAndVariantId(item.getProductId(), item.getVariantId())
                .orElseThrow(() -> new InvalidCommandException(
                        "Inventory not found by productId=" + item.getProductId() + ", variantId=" + item.getVariantId()));
    }

    private Inventory reserveWithOptimisticRetry(Inventory inventory, Integer quantity) {
        if (quantity == null || quantity <= 0) {
            throw new InvalidCommandException("Reservation quantity must be > 0");
        }

        for (int retry = 0; retry < MAX_OPTIMISTIC_RETRIES; retry++) {
            try {
                Inventory current = inventoryRepository.findById(inventory.getId())
                        .orElseThrow(() -> new InvalidCommandException("Inventory not found: " + inventory.getId()));

                int available = current.getTotalQuantity() - current.getReservedQuantity();
                if (available < quantity) {
                    throw new InsufficientStockException("Insufficient stock for sku " + current.getSku());
                }

                current.setReservedQuantity(current.getReservedQuantity() + quantity);
                return inventoryRepository.saveAndFlush(current);
            } catch (ObjectOptimisticLockingFailureException ex) {
                if (retry == MAX_OPTIMISTIC_RETRIES - 1) {
                    throw new InvalidCommandException("Optimistic lock failed while reserving stock");
                }
            }
        }

        throw new InvalidCommandException("Unexpected reserve retry failure");
    }

    private void commitWithOptimisticRetry(UUID inventoryId, Integer quantity) {
        for (int retry = 0; retry < MAX_OPTIMISTIC_RETRIES; retry++) {
            try {
                Inventory current = inventoryRepository.findById(inventoryId)
                        .orElseThrow(() -> new InvalidCommandException("Inventory not found: " + inventoryId));

                if (current.getReservedQuantity() < quantity || current.getTotalQuantity() < quantity) {
                    throw new InvalidCommandException("Quantity underflow for inventory " + inventoryId);
                }

                current.setReservedQuantity(current.getReservedQuantity() - quantity);
                current.setTotalQuantity(current.getTotalQuantity() - quantity);
                inventoryRepository.saveAndFlush(current);
                return;
            } catch (ObjectOptimisticLockingFailureException ex) {
                if (retry == MAX_OPTIMISTIC_RETRIES - 1) {
                    throw new InvalidCommandException("Optimistic lock failed while committing stock");
                }
            }
        }
    }

    private void releaseWithOptimisticRetry(UUID inventoryId, Integer quantity) {
        for (int retry = 0; retry < MAX_OPTIMISTIC_RETRIES; retry++) {
            try {
                Inventory current = inventoryRepository.findById(inventoryId)
                        .orElseThrow(() -> new InvalidCommandException("Inventory not found: " + inventoryId));

                if (current.getReservedQuantity() < quantity) {
                    throw new InvalidCommandException("Reserved quantity underflow for inventory " + inventoryId);
                }

                current.setReservedQuantity(current.getReservedQuantity() - quantity);
                inventoryRepository.saveAndFlush(current);
                return;
            } catch (ObjectOptimisticLockingFailureException ex) {
                if (retry == MAX_OPTIMISTIC_RETRIES - 1) {
                    throw new InvalidCommandException("Optimistic lock failed while releasing stock");
                }
            }
        }
    }

    private InventoryReservation findActiveReservation(UUID orderId, UUID reservationId) {
        if (reservationId != null) {
            return reservationRepository.findByIdAndStatus(reservationId, ReservationStatus.ACTIVE)
                    .orElseThrow(() -> new InvalidCommandException("Active reservation not found: " + reservationId));
        }

        if (orderId != null) {
            return reservationRepository.findByOrderIdAndStatus(orderId, ReservationStatus.ACTIVE)
                    .orElseThrow(() -> new InvalidCommandException("Active reservation not found for order: " + orderId));
        }

        throw new InvalidCommandException("Command requires orderId or reservationId");
    }

    private void cancelReservationInternal(InventoryReservation reservation, ReservationStatus targetStatus) {
        for (InventoryReservationItem item : reservation.getItems()) {
            releaseWithOptimisticRetry(item.getInventory().getId(), item.getQuantity());
        }
        reservation.setStatus(targetStatus);
        reservationRepository.save(reservation);
    }
}
