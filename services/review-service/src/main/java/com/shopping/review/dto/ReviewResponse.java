package com.shopping.review.dto;

import com.shopping.review.domain.SizeFeedback;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ReviewResponse {
    UUID id;
    UUID productId;
    UUID userId;
    Integer rating;
    String title;
    String content;
    SizeFeedback sizeFeedback;
    Integer qualityRating;
    Boolean verifiedPurchase;
    Integer helpfulCount;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
}
