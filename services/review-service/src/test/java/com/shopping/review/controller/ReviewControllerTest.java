package com.shopping.review.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopping.review.dto.CreateReviewRequest;
import com.shopping.review.dto.ReviewResponse;
import com.shopping.review.service.ReviewService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ReviewController.class)
class ReviewControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ReviewService reviewService;

    @Test
    @DisplayName("리뷰 생성 API")
    void createReview_Api() throws Exception {
        // given
        CreateReviewRequest request = new CreateReviewRequest();
        request.setProductId(UUID.randomUUID());
        request.setUserId(UUID.randomUUID());
        request.setRating(5);
        request.setTitle("Good");
        request.setContent("Very good");

        ReviewResponse response = ReviewResponse.builder()
                .id(UUID.randomUUID())
                .productId(request.getProductId())
                .rating(5)
                .build();

        when(reviewService.createReview(any(CreateReviewRequest.class))).thenReturn(response);

        // when & then
        mockMvc.perform(post("/api/reviews")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rating").value(5));
    }

    @Test
    @DisplayName("상품별 리뷰 목록 조회 API")
    void getReviewsByProduct_Api() throws Exception {
        // given
        UUID productId = UUID.randomUUID();
        ReviewResponse response = ReviewResponse.builder()
                .id(UUID.randomUUID())
                .productId(productId)
                .rating(4)
                .build();

        when(reviewService.getReviewsByProduct(
                eq(productId), anyInt(), anyInt(), any(), any(), any(), any(), any()
        )).thenReturn(new PageImpl<>(List.of(response)));

        // when & then
        mockMvc.perform(get("/api/reviews/product/{productId}", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].rating").value(4));
    }
}
