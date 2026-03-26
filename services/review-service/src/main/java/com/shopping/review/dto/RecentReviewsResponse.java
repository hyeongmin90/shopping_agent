package com.shopping.review.dto;

import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class RecentReviewsResponse {
    private Double averageRating;
    private long totalReviews;
    private List<ReviewResponse> reviews;
}
