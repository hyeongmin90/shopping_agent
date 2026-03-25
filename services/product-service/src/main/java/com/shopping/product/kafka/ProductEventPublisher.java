package com.shopping.product.kafka;

import com.shopping.product.dto.ProductResponse;
import java.time.OffsetDateTime;
import java.util.Objects;
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
public class ProductEventPublisher {

    private final KafkaTemplate<String, ProductViewedEvent> productViewedKafkaTemplate;
    private final KafkaTemplate<String, ProductIndexEvent> productIndexKafkaTemplate;

    @Value("${app.kafka.topics.product-viewed}")
    private String productViewedTopic;

    @Value("${app.kafka.topics.product-events}")
    private String productEventsTopic;

    public void publishProductViewed(ProductResponse product) {
        ProductViewedEvent event = ProductViewedEvent.builder()
            .eventId(UUID.randomUUID())
            .productId(product.id())
            .productName(product.name())
            .categoryId(product.categoryId())
            .brand(product.brand())
            .viewedAt(OffsetDateTime.now())
            .correlationId(MDC.get("correlationId"))
            .build();

        productViewedKafkaTemplate.send(productViewedTopic, product.id().toString(), event)
            .whenComplete((result, throwable) -> {
                if (throwable != null) {
                    log.error("Failed to publish ProductViewed event for productId={}", product.id(), throwable);
                    return;
                }
                log.info("Published ProductViewed event for productId={} partition={} offset={}",
                    product.id(),
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset());
            });
    }

    public void publishProductIndexEvent(ProductResponse product, String eventType) {
        ProductIndexEvent event = ProductIndexEvent.builder()
            .eventId(UUID.randomUUID())
            .eventType(eventType)
            .productId(product.id())
            .name(Objects.toString(product.name(), ""))
            .description(Objects.toString(product.description(), ""))
            .brand(Objects.toString(product.brand(), ""))
            .categoryName(Objects.toString(product.categoryName(), ""))
            .basePrice(product.basePrice())
            .currency(product.currency())
            .status(product.status())
            .occurredAt(OffsetDateTime.now())
            .correlationId(MDC.get("correlationId"))
            .build();

        productIndexKafkaTemplate.send(productEventsTopic, product.id().toString(), event)
            .whenComplete((result, throwable) -> {
                if (throwable != null) {
                    log.error("Failed to publish {} event for productId={}", eventType, product.id(), throwable);
                    return;
                }
                log.info("Published {} event for productId={} partition={} offset={}",
                    eventType,
                    product.id(),
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset());
            });
    }
}
