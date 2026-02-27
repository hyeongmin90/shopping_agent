package com.shopping.inventory.repository;

import com.shopping.inventory.entity.InventoryReservationItem;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryReservationItemRepository extends JpaRepository<InventoryReservationItem, UUID> {
}
