package com.shopping.review.kafka;

import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReviewEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${app.kafka.topics.review-events:review.events}")
    private String topic;

    public void publishReviewCreated(ReviewCreatedEvent eventData) {
        EventEnvelope<ReviewCreatedEvent> envelope = EventEnvelope.<ReviewCreatedEvent>builder()
                .meta(EventEnvelope.EventMeta.builder()
                        .eventId(UUID.randomUUID())
                        .eventType("ReviewCreatedEvent")
                        .occurredAt(Instant.now())
                        .correlationId(MDC.get("correlationId"))
                        .build())
                .data(eventData)
                .build();

        kafkaTemplate.send(topic, eventData.reviewId().toString(), envelope)
                .whenComplete((result, throwable) -> {
                    if (throwable != null) {
                        log.error("Failed to publish ReviewCreatedEvent for reviewId={}", eventData.reviewId(), throwable);
                    } else {
                        log.info("Successfully published ReviewCreatedEvent for reviewId={} to topic={}", 
                                eventData.reviewId(), topic);
                    }
                });
    }
}
