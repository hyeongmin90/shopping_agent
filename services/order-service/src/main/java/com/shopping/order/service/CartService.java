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
import com.shopping.order.repository.CartItemRepository;
import com.shopping.order.repository.CartRepository;
import com.shopping.order.repository.OrderRepository;
import com.shopping.order.dto.CheckoutResponse;
import com.shopping.order.dto.CheckoutRequest;
import com.shopping.order.client.ProductDto;
import com.shopping.order.client.ProductServiceClient;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final OrderRepository orderRepository;
    private final ProductServiceClient productServiceClient;

    @Transactional
    public CartResponse getCart(String userId) {
        CartEntity cart = getOrCreateCart(userId);
        return toResponse(cart);
    }

    @Transactional
    public CartResponse addItem(String userId, CartItemRequest request) {
        CartEntity cart = getOrCreateCart(userId);

        CartItemEntity item = new CartItemEntity();
        item.setCart(cart);
        item.setProductId(request.getProductId());
        item.setVariantId(request.getVariantId());
        item.setQuantity(request.getQuantity());

        item = cartItemRepository.save(item);
        cart.getItems().add(item);

        recalculateTotal(cart);
        cartRepository.save(cart);
        return toResponse(cart);
    }

    @Transactional
    public CartResponse removeItem(String userId, UUID itemId) {
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
    public CartResponse updateItem(String userId, UUID itemId, CartItemRequest request) {
        CartEntity cart = getOrCreateCart(userId);

        CartItemEntity item = cart.getItems().stream()
                .filter(existing -> existing.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Cart item not found"));

        item.setQuantity(request.getQuantity());
        item.setVariantId(request.getVariantId());

        recalculateTotal(cart);
        cartRepository.save(cart);
        return toResponse(cart);
    }

    @Transactional
    public CheckoutResponse checkout(String userId, CheckoutRequest request) {
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
            String productName = "Unknown Product";
            Integer unitPrice = 0;
            try {
                ProductDto product = productServiceClient.getProduct(cartItem.getProductId());
                productName = product.getName();
                unitPrice = product.getBasePrice();
            } catch (Exception e) {
                // fallback if product-service is unavailable
            }

            OrderItem orderItem = new OrderItem();
            orderItem.setOrder(order);
            orderItem.setProductId(cartItem.getProductId());
            orderItem.setVariantId(cartItem.getVariantId());
            orderItem.setProductName(productName);
            orderItem.setQuantity(cartItem.getQuantity());
            orderItem.setUnitPrice(unitPrice);
            order.getItems().add(orderItem);
        }

        order.setTotalAmount(cart.getTotalAmount());
        order.setShippingAddress(request.getShippingAddress());

        order = orderRepository.save(order);

        // Clear cart
        cart.getItems().clear();
        cart.setTotalAmount(0);
        cartRepository.save(cart);

        return new CheckoutResponse(order.getId(), order.getStatus(), order.getTotalAmount(),
                order.getCurrency());
    }

    private CartEntity getOrCreateCart(String userId) {
        return cartRepository.findByUserId(userId).orElseGet(() -> {
            CartEntity newCart = new CartEntity();
            newCart.setUserId(userId);
            return cartRepository.save(newCart);
        });
    }

    private void recalculateTotal(CartEntity cart) {
        int total = cart.getItems().stream()
                .mapToInt(item -> {
                    try {
                        ProductDto product = productServiceClient.getProduct(item.getProductId());
                        return item.getQuantity() * product.getBasePrice();
                    } catch (Exception e) {
                        return 0; // fallback if product-service unavailable
                    }
                })
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
                        .map(item -> {
                            String productName = "Unknown Product";
                            Integer unitPrice = 0;
                            try {
                                ProductDto product = productServiceClient.getProduct(item.getProductId());
                                productName = product.getName();
                                unitPrice = product.getBasePrice();
                            } catch (Exception e) {
                                // fallback if product-service is unavailable
                            }

                            return CartResponse.CartItemView.builder()
                                    .id(item.getId())
                                    .productId(item.getProductId())
                                    .variantId(item.getVariantId())
                                    .productName(productName)
                                    .quantity(item.getQuantity())
                                    .unitPrice(unitPrice)
                                    .build();
                        })
                        .toList())
                .build();
    }
}
