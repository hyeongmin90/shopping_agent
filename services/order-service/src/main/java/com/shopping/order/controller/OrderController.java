package com.shopping.order.controller;

import com.shopping.order.dto.ApproveOrderRequest;
import com.shopping.order.dto.CancelOrderRequest;
import com.shopping.order.dto.CheckoutRequest;
import com.shopping.order.dto.CheckoutResponse;
import com.shopping.order.dto.CreateOrderRequest;
import com.shopping.order.dto.OrderItemRequest;
import com.shopping.order.dto.OrderResponse;
import com.shopping.order.dto.OrderStatusResponse;
import com.shopping.order.dto.RefundRequest;
import com.shopping.order.service.OrderService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse createDraftOrder(@Valid @RequestBody CreateOrderRequest request) {
        return orderService.createOrder(request);
    }

    @GetMapping("/{id}")
    public OrderResponse getOrder(@PathVariable("id") UUID id) {
        return orderService.getOrder(id);
    }

    @GetMapping("/user/{userId}")
    public List<OrderResponse> getOrdersByUser(@PathVariable UUID userId) {
        return orderService.getOrdersByUser(userId);
    }

    @PostMapping("/{id}/items")
    public OrderResponse addItem(@PathVariable("id") UUID id, @Valid @RequestBody OrderItemRequest request) {
        return orderService.addItem(id, request);
    }

    @DeleteMapping("/{id}/items/{itemId}")
    public OrderResponse removeItem(@PathVariable("id") UUID id, @PathVariable UUID itemId) {
        return orderService.removeItem(id, itemId);
    }

    @PutMapping("/{id}/items/{itemId}")
    public OrderResponse updateItem(
            @PathVariable("id") UUID id,
            @PathVariable UUID itemId,
            @Valid @RequestBody OrderItemRequest request
    ) {
        return orderService.updateItem(id, itemId, request);
    }

    @PostMapping("/{id}/checkout")
    public CheckoutResponse checkout(@PathVariable("id") UUID id, @RequestBody(required = false) CheckoutRequest request) {
        return orderService.checkout(id, request == null ? new CheckoutRequest() : request);
    }

    @PostMapping("/{id}/approve")
    public OrderStatusResponse approve(
            @PathVariable("id") UUID id,
            @RequestBody(required = false) ApproveOrderRequest request
    ) {
        return orderService.approve(id, request == null ? new ApproveOrderRequest() : request);
    }

    @PostMapping("/{id}/cancel")
    public OrderStatusResponse cancel(
            @PathVariable("id") UUID id,
            @RequestBody(required = false) CancelOrderRequest request
    ) {
        return orderService.cancel(id, request == null ? null : request.getReason());
    }

    @PostMapping("/{id}/refund")
    public OrderStatusResponse refund(
            @PathVariable("id") UUID id,
            @RequestBody(required = false) RefundRequest request
    ) {
        return orderService.refund(id, request == null ? new RefundRequest() : request);
    }
}
