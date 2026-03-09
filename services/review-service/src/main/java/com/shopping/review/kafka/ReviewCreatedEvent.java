package com.shopping.review.kafka;

import com.shopping.review.domain.SizeFeedback;
import java.util.UUID;
import lombok.Builder;

@Builder
public record ReviewCreatedEvent(
    UUID reviewId,
    UUID productId,
    String productName,
    UUID userId,
    Integer rating,
    String title,
    String content,
    SizeFeedback sizeFeedback,
    Integer qualityRating,
    Boolean verifiedPurchase,
    Integer helpfulCount
) {
}
