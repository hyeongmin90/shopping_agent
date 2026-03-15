package com.shopping.review.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shopping.review.domain.Review;
import com.shopping.review.dto.CreateReviewRequest;
import com.shopping.review.dto.ReviewResponse;
import com.shopping.review.outbox.OutboxService;
import com.shopping.review.repository.ReviewRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private OutboxService outboxService;

    @InjectMocks
    private ReviewService reviewService;

    @Test
    @DisplayName("리뷰 생성 성공")
    void createReview_Success() {
        // given
        UUID productId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        CreateReviewRequest request = new CreateReviewRequest();
        request.setProductId(productId);
        request.setUserId(userId);
        request.setRating(5);
        request.setTitle("Great product");
        request.setContent("Highly recommended");
        request.setQualityRating(5);
        request.setVerifiedPurchase(true);

        Review savedReview = Review.builder()
                .id(UUID.randomUUID())
                .productId(productId)
                .userId(userId)
                .rating(5)
                .title("Great product")
                .content("Highly recommended")
                .qualityRating(5)
                .verifiedPurchase(true)
                .helpfulCount(0)
                .build();

        when(reviewRepository.save(any(Review.class))).thenReturn(savedReview);

        // when
        ReviewResponse response = reviewService.createReview(request);

        // then
        assertThat(response.getProductId()).isEqualTo(productId);
        assertThat(response.getRating()).isEqualTo(5);
        assertThat(response.getQualityRating()).isEqualTo(5);
        assertThat(response.getQualityRating()).isEqualTo(5);
        verify(reviewRepository).save(any(Review.class));
        verify(outboxService).enqueue(
                eq("Review"),
                eq(savedReview.getId()),
                eq("ReviewCreatedEvent"),
                any(),
                any(),
                isNull(),
                eq("review-created-" + savedReview.getId()));
    }

    @Test
    @DisplayName("상품별 리뷰 조회 성공")
    void getReviewsByProduct_Success() {
        // given
        UUID productId = UUID.randomUUID();
        Review review = Review.builder()
                .id(UUID.randomUUID())
                .productId(productId)
                .rating(4)
                .title("Good")
                .build();
        Page<Review> page = new PageImpl<>(List.of(review));

        when(reviewRepository.findByProductWithFilters(
                eq(productId), any(), any(), anyBoolean(), any(Pageable.class)
        )).thenReturn(page);

        // when
        Page<ReviewResponse> result = reviewService.getReviewsByProduct(
                productId, 0, 10, "createdAt", "desc", null, null, false
        );

        // then
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getRating()).isEqualTo(4);
    }

    @Test
    @DisplayName("잘못된 정렬 조건으로 리뷰 조회 시 실패")
    void getReviewsByProduct_InvalidSort() {
        // given
        UUID productId = UUID.randomUUID();

        // when & then
        assertThatThrownBy(() -> reviewService.getReviewsByProduct(
                productId, 0, 10, "invalidField", "desc", null, null, false
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Unsupported sortBy");
    }
}
