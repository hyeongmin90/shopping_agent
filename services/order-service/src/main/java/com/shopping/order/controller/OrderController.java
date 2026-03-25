package com.shopping.order.controller;

import com.shopping.order.dto.ApproveOrderRequest;
import com.shopping.order.dto.CancelOrderRequest;
import com.shopping.order.dto.OrderResponse;
import com.shopping.order.dto.OrderStatusResponse;
import com.shopping.order.dto.RefundRequest;
import com.shopping.order.service.OrderService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @GetMapping("/{id}")
    public OrderResponse getOrder(@PathVariable("id") UUID id) {
        return orderService.getOrder(id);
    }

    @GetMapping("/user/{userId}")
    public List<OrderResponse> getOrdersByUser(@PathVariable String userId) {
        return orderService.getOrdersByUser(userId);
    }

    @PostMapping("/{id}/approve")
    public OrderStatusResponse approve(
            @PathVariable("id") UUID id,
            @RequestBody(required = false) ApproveOrderRequest request) {
        return orderService.approve(id, request == null ? new ApproveOrderRequest() : request);
    }

    @PostMapping("/{id}/cancel")
    public OrderStatusResponse cancel(
            @PathVariable("id") UUID id,
            @RequestBody(required = false) CancelOrderRequest request) {
        return orderService.cancel(id, request == null ? null : request.getReason());
    }

    @PostMapping("/{id}/refund")
    public OrderStatusResponse refund(
            @PathVariable("id") UUID id,
            @RequestBody(required = false) RefundRequest request) {
        return orderService.refund(id, request == null ? new RefundRequest() : request);
    }
}
