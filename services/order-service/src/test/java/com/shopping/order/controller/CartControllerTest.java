package com.shopping.order.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopping.order.dto.CartItemRequest;
import com.shopping.order.dto.CartResponse;
import com.shopping.order.dto.CheckoutResponse;
import com.shopping.order.enums.OrderStatus;
import com.shopping.order.service.CartService;
import java.util.ArrayList;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CartController.class)
class CartControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CartService cartService;

    @Test
    @DisplayName("장바구니 조회 API")
    void getCart_Api() throws Exception {
        // given
        UUID userId = UUID.randomUUID();
        CartResponse response = CartResponse.builder()
                .userId(userId)
                .items(new ArrayList<>())
                .totalAmount(0)
                .build();

        when(cartService.getCart(userId)).thenReturn(response);

        // when & then
        mockMvc.perform(get("/api/carts/user/{userId}", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(userId.toString()));
    }

    @Test
    @DisplayName("장바구니 아이템 추가 API")
    void addItem_Api() throws Exception {
        // given
        UUID userId = UUID.randomUUID();
        CartItemRequest request = new CartItemRequest();
        request.setProductId(UUID.randomUUID());
        request.setQuantity(1);

        CartResponse response = CartResponse.builder()
                .userId(userId)
                .totalAmount(1000)
                .build();

        when(cartService.addItem(eq(userId), any(CartItemRequest.class))).thenReturn(response);

        // when & then
        mockMvc.perform(post("/api/carts/user/{userId}/items", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAmount").value(1000));
    }

    @Test
    @DisplayName("체크아웃 API")
    void checkout_Api() throws Exception {
        // given
        UUID userId = UUID.randomUUID();
        CheckoutResponse response = new CheckoutResponse(UUID.randomUUID(), OrderStatus.PENDING_APPROVAL, 1000, "KRW");

        when(cartService.checkout(eq(userId), any())).thenReturn(response);

        // when & then
        mockMvc.perform(post("/api/carts/user/{userId}/checkout", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"));
    }
}
