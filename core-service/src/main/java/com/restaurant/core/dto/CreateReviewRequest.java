package com.restaurant.core.dto;

import com.restaurant.core.domain.Review;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;

public record CreateReviewRequest(
        @NotNull            Long menuId,
        @Min(1) @Max(5)    int rating,
        @NotBlank @Size(max = 500) String content
) {}
