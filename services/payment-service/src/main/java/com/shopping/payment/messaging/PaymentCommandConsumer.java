package com.shopping.payment.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopping.payment.exception.InvalidPaymentStateException;
import com.shopping.payment.exception.ResourceNotFoundException;
import com.shopping.payment.messaging.model.AuthorizePaymentCommand;
import com.shopping.payment.messaging.model.CapturePaymentCommand;
import com.shopping.payment.messaging.model.EventEnvelope;
import com.shopping.payment.messaging.model.EventMeta;
import com.shopping.payment.messaging.model.RefundPaymentCommand;
import com.shopping.payment.messaging.model.VoidPaymentCommand;
import com.shopping.payment.service.CommandProcessingResult;
import com.shopping.payment.service.IdempotencyService;
import com.shopping.payment.service.PaymentCommandService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentCommandConsumer {

    private final ObjectMapper objectMapper;
    private final PaymentCommandService paymentCommandService;
    private final PaymentEventProducer paymentEventProducer;
    private final IdempotencyService idempotencyService;

    @Value("${payment.kafka.consumer-id:payment-command-consumer}")
    private String consumerId;

    @KafkaListener(topics = "${payment.kafka.commands-topic:payment.commands}", groupId = "${spring.kafka.consumer.group-id}")
    public void consume(String message) {
        EventEnvelope<JsonNode> envelope;
        try {
            envelope = readEnvelope(message);
        } catch (IllegalArgumentException exception) {
            log.error("Skipping invalid command payload", exception);
            return;
        }

        EventMeta meta = envelope.getMeta();
        if (meta == null || meta.getEventId() == null || meta.getEventType() == null) {
            log.warn("Skipping malformed command envelope: {}", message);
            return;
        }

        UUID eventId;
        try {
            eventId = UUID.fromString(meta.getEventId());
        } catch (IllegalArgumentException exception) {
            log.warn("Skipping command with invalid eventId={}", meta.getEventId());
            return;
        }

        if (idempotencyService.isProcessed(consumerId, eventId)) {
            log.info("Skipping duplicate command eventId={}", eventId);
            return;
        }

        try {
            CommandProcessingResult result = switch (meta.getEventType()) {
                case "AuthorizePaymentCommand" -> paymentCommandService.handleAuthorize(
                        objectMapper.treeToValue(envelope.getData(), AuthorizePaymentCommand.class),
                        resolveIdempotencyKey(meta));
                case "CapturePaymentCommand" -> paymentCommandService.handleCapture(
                        objectMapper.treeToValue(envelope.getData(), CapturePaymentCommand.class));
                case "VoidPaymentCommand" -> paymentCommandService.handleVoid(
                        objectMapper.treeToValue(envelope.getData(), VoidPaymentCommand.class));
                case "RefundPaymentCommand" -> paymentCommandService.handleRefund(
                        objectMapper.treeToValue(envelope.getData(), RefundPaymentCommand.class));
                default -> {
                    log.warn("Unsupported payment command type: {}", meta.getEventType());
                    yield null;
                }
            };

            if (result != null) {
                paymentEventProducer.publishEvent(result.eventType(), result.eventData(), meta);
            }

            idempotencyService.markProcessed(consumerId, eventId);
        } catch (InvalidPaymentStateException | ResourceNotFoundException knownException) {
            log.warn("Business command handling issue for eventId={}: {}", eventId, knownException.getMessage());
            idempotencyService.markProcessed(consumerId, eventId);
        } catch (JsonProcessingException parsingException) {
            log.error("Unable to parse payment command eventId={}", eventId, parsingException);
            idempotencyService.markProcessed(consumerId, eventId);
        }
    }

    private EventEnvelope<JsonNode> readEnvelope(String message) {
        try {
            return objectMapper.readValue(message, new TypeReference<>() {
            });
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Invalid command envelope payload", exception);
        }
    }

    private String resolveIdempotencyKey(EventMeta meta) {
        if (meta.getIdempotencyKey() != null && !meta.getIdempotencyKey().isBlank()) {
            return meta.getIdempotencyKey();
        }
        return meta.getEventId();
    }
}
