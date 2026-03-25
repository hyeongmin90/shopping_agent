package com.shopping.order.controller;

import com.shopping.order.dto.CartItemRequest;
import com.shopping.order.dto.CartResponse;
import com.shopping.order.dto.CheckoutRequest;
import com.shopping.order.dto.CheckoutResponse;
import com.shopping.order.service.CartService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/carts")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @GetMapping("/user/{userId}")
    public CartResponse getCart(@PathVariable String userId) {
        return cartService.getCart(userId);
    }

    @PostMapping("/user/{userId}/items")
    public CartResponse addItem(@PathVariable String userId, @Valid @RequestBody CartItemRequest request) {
        return cartService.addItem(userId, request);
    }

    @DeleteMapping("/user/{userId}/items/{itemId}")
    public CartResponse removeItem(@PathVariable String userId, @PathVariable UUID itemId) {
        return cartService.removeItem(userId, itemId);
    }

    @PutMapping("/user/{userId}/items/{itemId}")
    public CartResponse updateItem(
            @PathVariable String userId,
            @PathVariable UUID itemId,
            @Valid @RequestBody CartItemRequest request) {
        return cartService.updateItem(userId, itemId, request);
    }

    @PostMapping("/user/{userId}/checkout")
    public CheckoutResponse checkout(
            @PathVariable String userId,
            @RequestBody(required = false) CheckoutRequest request) {
        return cartService.checkout(userId, request == null ? new CheckoutRequest() : request);
    }
}
