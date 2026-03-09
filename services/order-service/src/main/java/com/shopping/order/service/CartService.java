package com.shopping.order.service;

import com.shopping.order.dto.CartItemRequest;
import com.shopping.order.dto.CartResponse;
import com.shopping.order.entity.CartEntity;
import com.shopping.order.entity.CartItemEntity;
import com.shopping.order.entity.OrderEntity;
import com.shopping.order.entity.OrderItem;
import com.shopping.order.enums.OrderSagaStatus;
import com.shopping.order.enums.OrderStatus;
import com.shopping.order.exception.BadRequestException;
import com.shopping.order.exception.NotFoundException;
import com.shopping.order.repository.CartRepository;
import com.shopping.order.repository.OrderRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CartService {

    private final CartRepository cartRepository;
    private final OrderRepository orderRepository;

    @Transactional
    public CartResponse getCart(UUID userId) {
        CartEntity cart = getOrCreateCart(userId);
        return toResponse(cart);
    }

    @Transactional
    public CartResponse addItem(UUID userId, CartItemRequest request) {
        CartEntity cart = getOrCreateCart(userId);

        CartItemEntity item = new CartItemEntity();
        item.setCart(cart);
        item.setProductId(request.getProductId());
        item.setVariantId(request.getVariantId());
        item.setProductName(request.getProductName());
        item.setQuantity(request.getQuantity());
        item.setUnitPrice(request.getUnitPrice());
        cart.getItems().add(item);

        recalculateTotal(cart);
        cartRepository.save(cart);
        return toResponse(cart);
    }

    @Transactional
    public CartResponse removeItem(UUID userId, UUID itemId) {
        CartEntity cart = getOrCreateCart(userId);

        boolean removed = cart.getItems().removeIf(item -> item.getId().equals(itemId));
        if (!removed) {
            throw new NotFoundException("Cart item not found");
        }

        recalculateTotal(cart);
        cartRepository.save(cart);
        return toResponse(cart);
    }

    @Transactional
    public CartResponse updateItem(UUID userId, UUID itemId, CartItemRequest request) {
        CartEntity cart = getOrCreateCart(userId);

        CartItemEntity item = cart.getItems().stream()
                .filter(existing -> existing.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Cart item not found"));

        item.setQuantity(request.getQuantity());
        item.setUnitPrice(request.getUnitPrice());
        item.setProductName(request.getProductName());
        item.setVariantId(request.getVariantId());

        recalculateTotal(cart);
        cartRepository.save(cart);
        return toResponse(cart);
    }

    @Transactional
    public com.shopping.order.dto.CheckoutResponse checkout(UUID userId,
            com.shopping.order.dto.CheckoutRequest request) {
        CartEntity cart = cartRepository.findByUserId(userId)
                .orElseThrow(() -> new NotFoundException("Cart not found for user: " + userId));

        if (cart.getItems().isEmpty()) {
            throw new BadRequestException("Cannot checkout empty cart");
        }

        OrderEntity order = new OrderEntity();
        order.setUserId(userId);
        order.setCurrency(cart.getCurrency());
        order.setStatus(OrderStatus.PENDING_APPROVAL);
        order.setSagaStatus(OrderSagaStatus.NONE);

        for (CartItemEntity cartItem : cart.getItems()) {
            OrderItem orderItem = new OrderItem();
            orderItem.setOrder(order);
            orderItem.setProductId(cartItem.getProductId());
            orderItem.setVariantId(cartItem.getVariantId());
            orderItem.setProductName(cartItem.getProductName());
            orderItem.setQuantity(cartItem.getQuantity());
            orderItem.setUnitPrice(cartItem.getUnitPrice());
            order.getItems().add(orderItem);
        }

        order.setTotalAmount(cart.getTotalAmount());
        order.setShippingAddress(request.getShippingAddress());

        order = orderRepository.save(order);

        // Clear cart
        cart.getItems().clear();
        cart.setTotalAmount(0);
        cartRepository.save(cart);

        return new com.shopping.order.dto.CheckoutResponse(order.getId(), order.getStatus(), order.getTotalAmount(),
                order.getCurrency());
    }

    private CartEntity getOrCreateCart(UUID userId) {
        return cartRepository.findByUserId(userId).orElseGet(() -> {
            CartEntity newCart = new CartEntity();
            newCart.setUserId(userId);
            return cartRepository.save(newCart);
        });
    }

    private void recalculateTotal(CartEntity cart) {
        int total = cart.getItems().stream()
                .mapToInt(item -> item.getQuantity() * item.getUnitPrice())
                .sum();
        cart.setTotalAmount(total);
    }

    private CartResponse toResponse(CartEntity cart) {
        return CartResponse.builder()
                .id(cart.getId())
                .userId(cart.getUserId())
                .currency(cart.getCurrency())
                .totalAmount(cart.getTotalAmount())
                .createdAt(cart.getCreatedAt())
                .updatedAt(cart.getUpdatedAt())
                .items(cart.getItems().stream()
                        .map(item -> CartResponse.CartItemView.builder()
                                .id(item.getId())
                                .productId(item.getProductId())
                                .variantId(item.getVariantId())
                                .productName(item.getProductName())
                                .quantity(item.getQuantity())
                                .unitPrice(item.getUnitPrice())
                                .build())
                        .toList())
                .build();
    }
}
