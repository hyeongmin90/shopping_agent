package com.shopping.inventory.repository;

import com.shopping.inventory.entity.Inventory;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryRepository extends JpaRepository<Inventory, UUID> {

    List<Inventory> findByProductId(UUID productId);

    Optional<Inventory> findByProductIdAndVariantId(UUID productId, UUID variantId);

    Optional<Inventory> findBySku(String sku);
}
