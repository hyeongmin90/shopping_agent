package com.shopping.review.dto;

import java.util.Map;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ReviewSummaryResponse {
    Double averageRating;
    Long totalReviews;
    Map<Integer, Long> ratingDistribution;
    Map<String, Long> sizeFeedbackDistribution;
    Double averageQualityRating;
}
