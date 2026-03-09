package com.shopping.inventory.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shopping.inventory.dto.InventoryResponse;
import com.shopping.inventory.dto.StockCheckResponse;
import com.shopping.inventory.service.InventoryQueryService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(InventoryController.class)
class InventoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private InventoryQueryService inventoryQueryService;

    @Test
    @DisplayName("상품별 재고 조회 API")
    void getByProduct_Api() throws Exception {
        // given
        UUID productId = UUID.randomUUID();
        InventoryResponse response = new InventoryResponse(
                UUID.randomUUID(), productId, UUID.randomUUID(), "SKU-001",
                100, 10, 90, 5, false
        );

        when(inventoryQueryService.getInventoryByProduct(productId)).thenReturn(List.of(response));

        // when & then
        mockMvc.perform(get("/api/inventory/product/{productId}", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sku").value("SKU-001"))
                .andExpect(jsonPath("$[0].availableQuantity").value(90));
    }

    @Test
    @DisplayName("재고 가용성 체크 API")
    void checkAvailability_Api() throws Exception {
        // given
        UUID productId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        StockCheckResponse response = new StockCheckResponse(productId, variantId, 10, 90, true, "OK");

        when(inventoryQueryService.checkAvailability(eq(productId), eq(variantId), anyInt())).thenReturn(response);

        // when & then
        mockMvc.perform(get("/api/inventory/check")
                .param("productId", productId.toString())
                .param("variantId", variantId.toString())
                .param("quantity", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true));
    }
}
