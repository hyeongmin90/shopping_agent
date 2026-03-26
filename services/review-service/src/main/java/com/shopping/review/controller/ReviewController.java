package com.shopping.review.controller;

import com.shopping.review.dto.CreateReviewRequest;
import com.shopping.review.dto.ReviewResponse;
import com.shopping.review.dto.ReviewSearchRequest;
import com.shopping.review.dto.RecentReviewsResponse;
import com.shopping.review.service.ReviewService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    @GetMapping("/product/{productId}")
    public Page<ReviewResponse> getReviewsByProduct(
            @PathVariable UUID productId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String direction,
            @RequestParam(required = false) Integer minRating,
            @RequestParam(required = false) Integer maxRating,
            @RequestParam(defaultValue = "false") Boolean verifiedOnly) {
        return reviewService.getReviewsByProduct(
                productId,
                page,
                size,
                sortBy,
                direction,
                minRating,
                maxRating,
                verifiedOnly);
    }

    @GetMapping("/product/{productId}/recent")
    public RecentReviewsResponse getRecentReviews(
            @PathVariable UUID productId,
            @RequestParam(defaultValue = "5") int limit) {
        return reviewService.getRecentReviews(productId, limit);
    }

    @GetMapping("/search")
    public Page<ReviewResponse> searchReviews(
            @RequestParam UUID productId,
            @RequestParam String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String direction,
            @RequestParam(required = false) Integer minRating,
            @RequestParam(required = false) Integer maxRating,
            @RequestParam(defaultValue = "false") Boolean verifiedOnly) {
        ReviewSearchRequest request = new ReviewSearchRequest();
        request.setProductId(productId);
        request.setKeyword(keyword);
        request.setPage(page);
        request.setSize(size);
        request.setSortBy(sortBy);
        request.setDirection(direction);
        request.setMinRating(minRating);
        request.setMaxRating(maxRating);
        request.setVerifiedOnly(verifiedOnly);
        return reviewService.searchReviews(request);
    }

    @PostMapping
    public ReviewResponse createReview(@Valid @RequestBody CreateReviewRequest request) {
        return reviewService.createReview(request);
    }
}
