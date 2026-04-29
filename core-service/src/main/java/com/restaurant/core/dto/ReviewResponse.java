package com.restaurant.core.dto;

import com.restaurant.core.domain.Review;
import java.time.LocalDateTime;

public record ReviewResponse(
        Long id,
        Long menuId,
        int rating,
        String content,
        Review.ReviewStatus status,
        LocalDateTime createdAt
) {
    public static ReviewResponse from(Review r) {
        return new ReviewResponse(
                r.getId(), r.getMenuId(), r.getRating(),
                r.getContent(), r.getStatus(), r.getCreatedAt()
        );
    }
}
