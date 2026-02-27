package com.shopping.inventory.controller;

import com.shopping.inventory.dto.InventoryResponse;
import com.shopping.inventory.dto.StockCheckResponse;
import com.shopping.inventory.service.InventoryQueryService;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/inventory")
public class InventoryController {

    private final InventoryQueryService inventoryQueryService;

    @GetMapping("/product/{productId}")
    public List<InventoryResponse> getByProduct(@PathVariable UUID productId) {
        return inventoryQueryService.getInventoryByProduct(productId);
    }

    @GetMapping("/check")
    public StockCheckResponse checkAvailability(
            @RequestParam @NotNull UUID productId,
            @RequestParam @NotNull UUID variantId,
            @RequestParam @NotNull @Min(1) Integer quantity
    ) {
        return inventoryQueryService.checkAvailability(productId, variantId, quantity);
    }

    @GetMapping("/sku/{sku}")
    public InventoryResponse getBySku(@PathVariable String sku) {
        return inventoryQueryService.getInventoryBySku(sku);
    }
}
