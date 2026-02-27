package com.shopping.inventory.service;

import com.shopping.inventory.dto.InventoryResponse;
import com.shopping.inventory.dto.StockCheckResponse;
import com.shopping.inventory.entity.Inventory;
import com.shopping.inventory.exception.InventoryNotFoundException;
import com.shopping.inventory.repository.InventoryRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InventoryQueryService {

    private final InventoryRepository inventoryRepository;

    @Cacheable(cacheNames = "inventoryByProduct", key = "#productId")
    public List<InventoryResponse> getInventoryByProduct(UUID productId) {
        return inventoryRepository.findByProductId(productId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Cacheable(cacheNames = "inventoryBySku", key = "#sku")
    public InventoryResponse getInventoryBySku(String sku) {
        Inventory inventory = inventoryRepository.findBySku(sku)
                .orElseThrow(() -> new InventoryNotFoundException("Inventory not found for sku: " + sku));
        return toResponse(inventory);
    }

    @Cacheable(cacheNames = "inventoryAvailability", key = "#productId.toString().concat('-').concat(#variantId.toString()).concat('-').concat(#quantity)")
    public StockCheckResponse checkAvailability(UUID productId, UUID variantId, Integer quantity) {
        Inventory inventory = inventoryRepository.findByProductIdAndVariantId(productId, variantId)
                .orElseThrow(() -> new InventoryNotFoundException(
                        "Inventory not found for productId " + productId + " and variantId " + variantId));

        int available = inventory.getAvailableQuantity() == null
                ? inventory.getTotalQuantity() - inventory.getReservedQuantity()
                : inventory.getAvailableQuantity();
        boolean canFulfill = available >= quantity;

        String message = canFulfill
                ? "Stock available"
                : "Insufficient stock. Requested=" + quantity + ", available=" + available;

        return new StockCheckResponse(
                productId,
                variantId,
                quantity,
                available,
                canFulfill,
                message
        );
    }

    private InventoryResponse toResponse(Inventory inventory) {
        int available = inventory.getAvailableQuantity() == null
                ? inventory.getTotalQuantity() - inventory.getReservedQuantity()
                : inventory.getAvailableQuantity();
        int lowStockThreshold = inventory.getLowStockThreshold() == null ? 0 : inventory.getLowStockThreshold();

        return new InventoryResponse(
                inventory.getId(),
                inventory.getProductId(),
                inventory.getVariantId(),
                inventory.getSku(),
                inventory.getTotalQuantity(),
                inventory.getReservedQuantity(),
                available,
                lowStockThreshold,
                available <= lowStockThreshold
        );
    }
}
