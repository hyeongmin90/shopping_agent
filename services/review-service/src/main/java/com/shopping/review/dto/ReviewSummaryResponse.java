package com.shopping.review.dto;

import java.io.Serializable;
import java.util.Map;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ReviewSummaryResponse implements Serializable {
    private static final long serialVersionUID = 1L;
    Double averageRating;
    Long totalReviews;
    Map<Integer, Long> ratingDistribution;
    Map<String, Long> sizeFeedbackDistribution;
    Double averageQualityRating;
}
