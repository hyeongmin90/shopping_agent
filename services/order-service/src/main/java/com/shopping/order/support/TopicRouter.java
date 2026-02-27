package com.shopping.order.support;

import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class TopicRouter {

    private final String orderEventsTopic;
    private final String orderCommandsTopic;
    private final String inventoryCommandsTopic;
    private final String paymentCommandsTopic;

    private static final Set<String> INVENTORY_EVENTS = Set.of(
            "ReserveInventoryCommand",
            "CommitInventoryCommand",
            "CancelInventoryReservationCommand"
    );

    private static final Set<String> PAYMENT_EVENTS = Set.of(
            "AuthorizePaymentCommand",
            "CapturePaymentCommand",
            "VoidPaymentCommand",
            "RefundPaymentCommand"
    );

    public TopicRouter(
            @Value("${app.kafka.topics.order-events}") String orderEventsTopic,
            @Value("${app.kafka.topics.order-commands}") String orderCommandsTopic,
            @Value("${app.kafka.topics.inventory-commands}") String inventoryCommandsTopic,
            @Value("${app.kafka.topics.payment-commands}") String paymentCommandsTopic
    ) {
        this.orderEventsTopic = orderEventsTopic;
        this.orderCommandsTopic = orderCommandsTopic;
        this.inventoryCommandsTopic = inventoryCommandsTopic;
        this.paymentCommandsTopic = paymentCommandsTopic;
    }

    public String route(String eventType) {
        if (INVENTORY_EVENTS.contains(eventType)) {
            return inventoryCommandsTopic;
        }
        if (PAYMENT_EVENTS.contains(eventType)) {
            return paymentCommandsTopic;
        }
        if (eventType.endsWith("Command")) {
            return orderCommandsTopic;
        }
        return orderEventsTopic;
    }
}
