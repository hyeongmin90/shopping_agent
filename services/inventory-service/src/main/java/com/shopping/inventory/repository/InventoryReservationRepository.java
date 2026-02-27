package com.shopping.inventory.repository;

import com.shopping.inventory.entity.InventoryReservation;
import com.shopping.inventory.enums.ReservationStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryReservationRepository extends JpaRepository<InventoryReservation, UUID> {

    @EntityGraph(attributePaths = {"items", "items.inventory"})
    Optional<InventoryReservation> findByOrderIdAndStatus(UUID orderId, ReservationStatus status);

    @EntityGraph(attributePaths = {"items", "items.inventory"})
    Optional<InventoryReservation> findByIdAndStatus(UUID id, ReservationStatus status);

    @EntityGraph(attributePaths = {"items", "items.inventory"})
    List<InventoryReservation> findByStatusAndExpiresAtBefore(ReservationStatus status, LocalDateTime expiresAt);
}
