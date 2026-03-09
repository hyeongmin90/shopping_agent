package com.shopping.order.service;

import com.shopping.order.dto.ApproveOrderRequest;
import com.shopping.order.dto.OrderResponse;
import com.shopping.order.dto.RefundRequest;
import com.shopping.order.entity.OrderEntity;
import com.shopping.order.entity.SagaState;
import com.shopping.order.enums.OrderSagaStatus;
import com.shopping.order.enums.OrderStatus;
import com.shopping.order.enums.SagaStatus;
import com.shopping.order.exception.BadRequestException;
import com.shopping.order.exception.NotFoundException;
import com.shopping.order.repository.OrderRepository;
import com.shopping.order.repository.SagaStateRepository;
import com.shopping.order.dto.OrderStatusResponse;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final SagaStateRepository sagaStateRepository;
    private final OutboxService outboxService;

    @Transactional(readOnly = true)
    public OrderResponse getOrder(UUID orderId) {
        OrderEntity order = findOrder(orderId);
        order.getItems().size();
        return toResponse(order);
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getOrdersByUser(UUID userId) {
        return orderRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    @CacheEvict(cacheNames = "order-status", key = "#orderId")
    public OrderStatusResponse approve(UUID orderId, ApproveOrderRequest request) {
        OrderEntity order = findOrder(orderId);
        ensureStatus(order, OrderStatus.PENDING_APPROVAL, "Order must be pending approval");

        order.setStatus(OrderStatus.PLACED);
        order.setSagaStatus(OrderSagaStatus.RUNNING);

        SagaState sagaState = sagaStateRepository.findByOrderId(orderId).orElseGet(SagaState::new);
        sagaState.setOrderId(orderId);
        sagaState.setCurrentStep("INVENTORY_RESERVATION");
        sagaState.setStatus(SagaStatus.RUNNING);
        sagaState.setTimeoutAt(LocalDateTime.now().plusMinutes(5));
        sagaStateRepository.save(sagaState);

        order.setStatus(OrderStatus.INVENTORY_RESERVING);
        orderRepository.save(order);

        String paymentMethod = request.getPaymentMethod() == null ? "MOCK" : request.getPaymentMethod();

        Map<String, Object> command = new HashMap<>();
        command.put("orderId", order.getId());
        command.put("items", order.getItems().stream().map(item -> {
            Map<String, Object> entry = new HashMap<>();
            entry.put("productId", item.getProductId());
            entry.put("variantId", item.getVariantId());
            entry.put("quantity", item.getQuantity());
            return entry;
        }).toList());
        command.put("requestedBy", order.getUserId());
        command.put("paymentMethod", paymentMethod);

        outboxService.enqueue(
                "ORDER",
                order.getId(),
                "ReserveInventoryCommand",
                command,
                order.getId(),
                null,
                "approve-" + order.getId());

        return new OrderStatusResponse(order.getId(), order.getStatus(), order.getSagaStatus().name(),
                "Saga started: inventory reservation requested");
    }

    @Transactional
    @CacheEvict(cacheNames = "order-status", key = "#orderId")
    public OrderStatusResponse cancel(UUID orderId, String reason) {
        OrderEntity order = findOrder(orderId);

        if (order.getStatus() == OrderStatus.CONFIRMED || order.getStatus() == OrderStatus.REFUNDED) {
            throw new BadRequestException("Confirmed/refunded order cannot be cancelled");
        }

        SagaState sagaState = sagaStateRepository.findByOrderId(orderId).orElse(null);
        if (sagaState != null && sagaState.getStatus() == SagaStatus.RUNNING) {
            sagaState.setStatus(SagaStatus.COMPENSATING);
            sagaState.setCurrentStep("CANCELLED_BY_USER");
            sagaStateRepository.save(sagaState);
            createCompensationCommands(order, "cancelled by user");
        }

        order.setStatus(OrderStatus.CANCELLED);
        order.setSagaStatus(OrderSagaStatus.COMPENSATING);
        order.setFailureReason(reason == null ? "Cancelled by user" : reason);
        orderRepository.save(order);

        outboxService.enqueue(
                "ORDER",
                order.getId(),
                "OrderCancelled",
                Map.of("orderId", order.getId(), "reason", order.getFailureReason()),
                order.getId(),
                null,
                "cancel-" + order.getId());

        return new OrderStatusResponse(order.getId(), order.getStatus(), order.getSagaStatus().name(),
                "Order cancelled");
    }

    @Transactional
    @CacheEvict(cacheNames = "order-status", key = "#orderId")
    public OrderStatusResponse refund(UUID orderId, RefundRequest request) {
        OrderEntity order = findOrder(orderId);
        ensureStatus(order, OrderStatus.CONFIRMED, "Only confirmed orders can be refunded");
        if (order.getPaymentId() == null) {
            throw new BadRequestException("No payment to refund");
        }

        int refundAmount = request.getAmount() == null ? order.getTotalAmount() : request.getAmount();
        order.setStatus(OrderStatus.REFUND_REQUESTED);
        orderRepository.save(order);

        outboxService.enqueue(
                "ORDER",
                order.getId(),
                "RefundPaymentCommand",
                Map.of(
                        "orderId", order.getId(),
                        "paymentId", order.getPaymentId(),
                        "refundAmount", refundAmount,
                        "reason", request.getReason() == null ? "Customer requested refund" : request.getReason()),
                order.getId(),
                null,
                "refund-" + order.getId());

        return new OrderStatusResponse(order.getId(), order.getStatus(), order.getSagaStatus().name(),
                "Refund requested");
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "order-status", key = "#orderId")
    public OrderStatusResponse getOrderStatus(UUID orderId) {
        OrderEntity order = findOrder(orderId);
        return new OrderStatusResponse(order.getId(), order.getStatus(), order.getSagaStatus().name(), "OK");
    }

    @Transactional
    public void markFailed(UUID orderId, String reason, String step) {
        OrderEntity order = findOrder(orderId);
        order.setStatus(OrderStatus.FAILED);
        order.setSagaStatus(OrderSagaStatus.FAILED);
        order.setFailureReason(reason);
        orderRepository.save(order);

        sagaStateRepository.findByOrderId(orderId).ifPresent(saga -> {
            saga.setStatus(SagaStatus.FAILED);
            saga.setCurrentStep(step);
            sagaStateRepository.save(saga);
        });

        outboxService.enqueue(
                "ORDER",
                order.getId(),
                "OrderFailed",
                Map.of("orderId", order.getId(), "reason", reason, "failedStep", step),
                order.getId(),
                null,
                "failed-" + order.getId() + "-" + step);
    }

    @Transactional
    public void markConfirmed(UUID orderId) {
        OrderEntity order = findOrder(orderId);
        order.setStatus(OrderStatus.CONFIRMED);
        order.setSagaStatus(OrderSagaStatus.COMPLETED);
        orderRepository.save(order);

        sagaStateRepository.findByOrderId(orderId).ifPresent(saga -> {
            saga.setStatus(SagaStatus.COMPLETED);
            saga.setCurrentStep("DONE");
            sagaStateRepository.save(saga);
        });

        outboxService.enqueue(
                "ORDER",
                order.getId(),
                "OrderConfirmed",
                Map.of("orderId", order.getId(), "confirmedAt", LocalDateTime.now().toString()),
                order.getId(),
                null,
                "confirmed-" + order.getId());
    }

    @Transactional
    public void moveToPaymentAuthorizing(UUID orderId, UUID reservationId) {
        OrderEntity order = findOrder(orderId);
        order.setReservationId(reservationId);
        order.setStatus(OrderStatus.PAYMENT_AUTHORIZING);
        orderRepository.save(order);

        SagaState sagaState = sagaStateRepository.findByOrderId(orderId)
                .orElseThrow(() -> new NotFoundException("Saga state not found for order " + orderId));
        sagaState.setCurrentStep("PAYMENT_AUTHORIZATION");
        sagaState.setTimeoutAt(LocalDateTime.now().plusMinutes(5));
        sagaStateRepository.save(sagaState);

        outboxService.enqueue(
                "ORDER",
                order.getId(),
                "AuthorizePaymentCommand",
                Map.of(
                        "orderId", order.getId(),
                        "userId", order.getUserId(),
                        "amount", order.getTotalAmount(),
                        "currency", order.getCurrency(),
                        "paymentMethod", "MOCK"),
                order.getId(),
                null,
                "authpay-" + order.getId());
    }

    @Transactional
    public void handlePaymentAuthorized(UUID orderId, UUID paymentId) {
        OrderEntity order = findOrder(orderId);
        order.setPaymentId(paymentId);
        orderRepository.save(order);

        outboxService.enqueue(
                "ORDER",
                order.getId(),
                "CommitInventoryCommand",
                Map.of("orderId", order.getId(), "reservationId", order.getReservationId()),
                order.getId(),
                null,
                "commit-inv-" + order.getId());
        outboxService.enqueue(
                "ORDER",
                order.getId(),
                "CapturePaymentCommand",
                Map.of("orderId", order.getId(), "paymentId", order.getPaymentId(), "captureAmount",
                        order.getTotalAmount()),
                order.getId(),
                null,
                "capture-" + order.getId());
    }

    @Transactional
    public void createCompensationCommands(OrderEntity order, String reason) {
        if (order.getReservationId() != null) {
            outboxService.enqueue(
                    "ORDER",
                    order.getId(),
                    "CancelInventoryReservationCommand",
                    Map.of("orderId", order.getId(), "reservationId", order.getReservationId()),
                    order.getId(),
                    null,
                    "cancel-inv-" + order.getId());
        }
        if (order.getPaymentId() != null) {
            outboxService.enqueue(
                    "ORDER",
                    order.getId(),
                    "VoidPaymentCommand",
                    Map.of("orderId", order.getId(), "paymentId", order.getPaymentId(), "reason", reason),
                    order.getId(),
                    null,
                    "void-pay-" + order.getId());
        }
    }

    @Transactional(readOnly = true)
    public List<SagaState> findTimedOutSagas(LocalDateTime now) {
        return sagaStateRepository.findByStatusAndTimeoutAtBefore(SagaStatus.RUNNING, now);
    }

    @Transactional
    public void markSagaCompensating(UUID orderId, String reason) {
        OrderEntity order = findOrder(orderId);
        order.setSagaStatus(OrderSagaStatus.COMPENSATING);
        order.setFailureReason(reason);
        order.setStatus(OrderStatus.FAILED);
        orderRepository.save(order);

        SagaState sagaState = sagaStateRepository.findByOrderId(orderId)
                .orElseThrow(() -> new NotFoundException("Saga state not found for order " + orderId));
        sagaState.setStatus(SagaStatus.COMPENSATING);
        sagaState.setCurrentStep("COMPENSATION");
        sagaStateRepository.save(sagaState);
    }

    private OrderEntity findOrder(UUID orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException("Order not found: " + orderId));
    }

    private void ensureStatus(OrderEntity order, OrderStatus required, String message) {
        if (order.getStatus() != required) {
            throw new BadRequestException(message);
        }
    }

    private OrderResponse toResponse(OrderEntity order) {
        return OrderResponse.builder()
                .id(order.getId())
                .userId(order.getUserId())
                .status(order.getStatus())
                .totalAmount(order.getTotalAmount())
                .currency(order.getCurrency())
                .shippingAddress(order.getShippingAddress())
                .failureReason(order.getFailureReason())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .items(order.getItems().stream()
                        .map(item -> OrderResponse.OrderItemView.builder()
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
