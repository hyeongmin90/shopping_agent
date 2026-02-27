package com.shopping.review.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import lombok.Data;

@Data
public class ReviewSearchRequest {

    @NotNull
    private UUID productId;

    @NotBlank
    private String keyword;

    @Min(0)
    private int page = 0;

    @Min(1)
    @Max(100)
    private int size = 20;

    private String sortBy = "createdAt";

    private String direction = "desc";

    @Min(1)
    @Max(5)
    private Integer minRating;

    @Min(1)
    @Max(5)
    private Integer maxRating;

    private Boolean verifiedOnly = false;
}
