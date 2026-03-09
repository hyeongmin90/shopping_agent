package com.shopping.review.service;

import com.shopping.review.domain.Review;
import com.shopping.review.domain.SizeFeedback;
import com.shopping.review.dto.CreateReviewRequest;
import com.shopping.review.dto.ReviewResponse;
import com.shopping.review.dto.ReviewSearchRequest;
import com.shopping.review.dto.ReviewSummaryResponse;
import com.shopping.review.kafka.ReviewCreatedEvent;
import com.shopping.review.kafka.ReviewEventPublisher;
import com.shopping.review.repository.RatingCountView;
import com.shopping.review.repository.ReviewRepository;
import com.shopping.review.repository.SizeFeedbackCountView;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReviewService {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("rating", "createdAt", "helpfulCount");
    private static final Map<String, String> JPA_TO_COLUMN = Map.of(
            "createdAt", "created_at",
            "helpfulCount", "helpful_count",
            "rating", "rating"
    );
    private final ReviewRepository reviewRepository;
    private final ReviewEventPublisher reviewEventPublisher;

    @Transactional(readOnly = true)
    public Page<ReviewResponse> getReviewsByProduct(
            UUID productId,
            int page,
            int size,
            String sortBy,
            String direction,
            Integer minRating,
            Integer maxRating,
            SizeFeedback sizeFeedback,
            Boolean verifiedOnly
    ) {
        validateRatingRange(minRating, maxRating);
        Pageable pageable = buildPageable(page, size, sortBy, direction);
        Page<Review> reviews = reviewRepository.findByProductWithFilters(
                productId,
                minRating,
                maxRating,
                sizeFeedback,
                Boolean.TRUE.equals(verifiedOnly),
                pageable
        );
        return reviews.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "reviewSummary", key = "#productId")
    public ReviewSummaryResponse getReviewSummary(UUID productId) {
        Double averageRating = reviewRepository.findAverageRatingByProductId(productId);
        Double averageQualityRating = reviewRepository.findAverageQualityRatingByProductId(productId);
        long totalReviews = reviewRepository.countByProductId(productId);

        Map<Integer, Long> ratingDistribution = Arrays.stream(new Integer[]{1, 2, 3, 4, 5})
                .collect(Collectors.toMap(Function.identity(), rating -> 0L));
        for (RatingCountView row : reviewRepository.countByRatingForProduct(productId)) {
            ratingDistribution.put(row.getRating(), row.getCount());
        }

        Map<String, Long> sizeFeedbackDistribution = Arrays.stream(SizeFeedback.values())
                .collect(Collectors.toMap(SizeFeedback::name, ignored -> 0L));
        for (SizeFeedbackCountView row : reviewRepository.countBySizeFeedbackForProduct(productId)) {
            sizeFeedbackDistribution.put(row.getSizeFeedback().name(), row.getCount());
        }

        return ReviewSummaryResponse.builder()
                .averageRating(roundToTwoDecimals(averageRating))
                .totalReviews(totalReviews)
                .ratingDistribution(ratingDistribution)
                .sizeFeedbackDistribution(sizeFeedbackDistribution)
                .averageQualityRating(roundToTwoDecimals(averageQualityRating))
                .build();
    }

    @Transactional(readOnly = true)
    public Page<ReviewResponse> searchReviews(ReviewSearchRequest request) {
        validateRatingRange(request.getMinRating(), request.getMaxRating());
        if (request.getKeyword() == null || request.getKeyword().trim().isEmpty()) {
            throw new IllegalArgumentException("keyword must not be blank");
        }
        Pageable pageable = buildNativePageable(request.getPage(), request.getSize(), request.getSortBy(), request.getDirection());
        Page<Review> reviews = reviewRepository.searchByKeywordWithinProduct(
                request.getProductId(),
                request.getKeyword().trim(),
                request.getMinRating(),
                request.getMaxRating(),
                Boolean.TRUE.equals(request.getVerifiedOnly()),
                pageable
        );
        return reviews.map(this::toResponse);
    }

    @Transactional
    @CacheEvict(value = "reviewSummary", key = "#request.productId")
    public ReviewResponse createReview(CreateReviewRequest request) {
        Review review = Review.builder()
                .productId(request.getProductId())
                .userId(request.getUserId())
                .rating(request.getRating())
                .title(request.getTitle())
                .content(request.getContent())
                .sizeFeedback(request.getSizeFeedback())
                .qualityRating(request.getQualityRating())
                .verifiedPurchase(Boolean.TRUE.equals(request.getVerifiedPurchase()))
                .helpfulCount(0)
                .build();

        Review saved = reviewRepository.save(review);

        // Publish event for RAG indexing
        reviewEventPublisher.publishReviewCreated(ReviewCreatedEvent.builder()
                .reviewId(saved.getId())
                .productId(saved.getProductId())
                .userId(saved.getUserId())
                .rating(saved.getRating())
                .title(saved.getTitle())
                .content(saved.getContent())
                .sizeFeedback(saved.getSizeFeedback())
                .qualityRating(saved.getQualityRating())
                .verifiedPurchase(saved.getVerifiedPurchase())
                .helpfulCount(saved.getHelpfulCount())
                .build());

        return toResponse(saved);
    }

    private Pageable buildPageable(int page, int size, String sortBy, String direction) {
        String normalizedSortBy = sortBy == null ? "createdAt" : sortBy;
        if (!ALLOWED_SORT_FIELDS.contains(normalizedSortBy)) {
            throw new IllegalArgumentException("Unsupported sortBy: " + normalizedSortBy);
        }

        Sort.Direction sortDirection = "asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;
        return PageRequest.of(page, size, Sort.by(sortDirection, normalizedSortBy));
    }

    private Pageable buildNativePageable(int page, int size, String sortBy, String direction) {
        String normalizedSortBy = sortBy == null ? "createdAt" : sortBy;
        if (!ALLOWED_SORT_FIELDS.contains(normalizedSortBy)) {
            throw new IllegalArgumentException("Unsupported sortBy: " + normalizedSortBy);
        }
        String columnName = JPA_TO_COLUMN.getOrDefault(normalizedSortBy, normalizedSortBy);
        Sort.Direction sortDirection = "asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;
        return PageRequest.of(page, size, Sort.by(sortDirection, columnName));
    }

    private void validateRatingRange(Integer minRating, Integer maxRating) {
        if (minRating != null && maxRating != null && minRating > maxRating) {
            throw new IllegalArgumentException("minRating must be less than or equal to maxRating");
        }
    }

    private Double roundToTwoDecimals(Double value) {
        if (value == null) {
            return null;
        }
        return Math.round(value * 100.0) / 100.0;
    }

    private ReviewResponse toResponse(Review review) {
        return ReviewResponse.builder()
                .id(review.getId())
                .productId(review.getProductId())
                .userId(review.getUserId())
                .rating(review.getRating())
                .title(review.getTitle())
                .content(review.getContent())
                .sizeFeedback(review.getSizeFeedback())
                .qualityRating(review.getQualityRating())
                .verifiedPurchase(review.getVerifiedPurchase())
                .helpfulCount(review.getHelpfulCount())
                .createdAt(review.getCreatedAt())
                .updatedAt(review.getUpdatedAt())
                .build();
    }
}
