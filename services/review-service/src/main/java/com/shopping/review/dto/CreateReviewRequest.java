package com.shopping.review.dto;

import com.shopping.review.domain.SizeFeedback;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import lombok.Data;

@Data
public class CreateReviewRequest {

    @NotNull
    private UUID productId;

    @NotNull
    private UUID userId;

    @NotNull
    @Min(1)
    @Max(5)
    private Integer rating;

    @Size(max = 200)
    private String title;

    @NotBlank
    private String content;

    private SizeFeedback sizeFeedback;

    @Min(1)
    @Max(5)
    private Integer qualityRating;

    private Boolean verifiedPurchase = false;
}
