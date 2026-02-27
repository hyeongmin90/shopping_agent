package com.shopping.order.saga;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopping.order.entity.IdempotencyRecord;
import com.shopping.order.entity.IdempotencyRecordId;
import com.shopping.order.entity.OrderEntity;
import com.shopping.order.exception.BadRequestException;
import com.shopping.order.repository.IdempotencyRecordRepository;
import com.shopping.order.repository.OrderRepository;
import com.shopping.order.service.OrderService;
import java.io.IOException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class SagaOrchestrator {

    private static final String CONSUMER_ID = "order-saga-orchestrator";

    private final ObjectMapper objectMapper;
    private final OrderService orderService;
    private final OrderRepository orderRepository;
    private final IdempotencyRecordRepository idempotencyRecordRepository;

    @Transactional
    @KafkaListener(topics = "${app.kafka.topics.inventory-events}", groupId = "order-service")
    public void onInventoryEvents(String payload) {
        process(payload);
    }

    @Transactional
    @KafkaListener(topics = "${app.kafka.topics.payment-events}", groupId = "order-service")
    public void onPaymentEvents(String payload) {
        process(payload);
    }

    private void process(String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            JsonNode meta = root.path("meta");
            JsonNode data = root.path("data");

            String eventType = text(meta, "eventType");
            UUID eventId = UUID.fromString(text(meta, "eventId"));
            UUID orderId = UUID.fromString(text(data, "orderId"));

            if (idempotencyRecordRepository.existsById(new IdempotencyRecordId(CONSUMER_ID, eventId))) {
                return;
            }

            switch (eventType) {
                case "InventoryReserved" -> onInventoryReserved(orderId, data);
                case "InventoryReservationFailed" -> onInventoryReservationFailed(orderId, data);
                case "PaymentAuthorized" -> onPaymentAuthorized(orderId, data);
                case "PaymentAuthorizationFailed" -> onPaymentAuthorizationFailed(orderId, data);
                case "PaymentCaptured" -> onPaymentCaptured(orderId);
                default -> log.debug("Ignoring unrelated event type {}", eventType);
            }

            IdempotencyRecord record = new IdempotencyRecord();
            record.setId(new IdempotencyRecordId(CONSUMER_ID, eventId));
            idempotencyRecordRepository.save(record);
        } catch (IOException e) {
            throw new IllegalStateException("Invalid event payload", e);
        }
    }

    private void onInventoryReserved(UUID orderId, JsonNode data) {
        UUID reservationId = UUID.fromString(text(data, "reservationId"));
        orderService.moveToPaymentAuthorizing(orderId, reservationId);
    }

    private void onInventoryReservationFailed(UUID orderId, JsonNode data) {
        String reason = text(data, "reason");
        orderService.markFailed(orderId, reason, "INVENTORY_RESERVATION");
    }

    private void onPaymentAuthorized(UUID orderId, JsonNode data) {
        UUID paymentId = UUID.fromString(text(data, "paymentId"));
        orderService.handlePaymentAuthorized(orderId, paymentId);
    }

    private void onPaymentAuthorizationFailed(UUID orderId, JsonNode data) {
        String reason = text(data, "reason");
        OrderEntity order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BadRequestException("Order not found for payment failure"));
        orderService.createCompensationCommands(order, reason);
        orderService.markFailed(orderId, reason, "PAYMENT_AUTHORIZATION");
    }

    private void onPaymentCaptured(UUID orderId) {
        orderService.markConfirmed(orderId);
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull() || value.asText().isBlank()) {
            throw new BadRequestException("Missing field " + field + " in event payload");
        }
        return value.asText();
    }
}
