package com.shopping.product.kafka;

import com.shopping.product.dto.ProductResponse;
import java.time.OffsetDateTime;
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

    @Value("${app.kafka.topics.product-viewed}")
    private String productViewedTopic;

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
}
