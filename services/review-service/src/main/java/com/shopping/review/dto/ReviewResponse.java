package com.shopping.review.dto;

import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ReviewResponse {
    UUID id;
    UUID productId;
    String userId;
    Integer rating;
    String title;
    String content;
    Integer qualityRating;
    Boolean verifiedPurchase;
    Integer helpfulCount;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
}
