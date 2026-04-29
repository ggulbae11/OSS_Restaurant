package com.restaurant.core.dto;

import com.restaurant.core.domain.Order;
import com.restaurant.core.domain.Order.OrderStatus;
import com.restaurant.core.domain.OrderItem;
import com.restaurant.core.domain.Review;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.LocalDateTime;
import java.util.List;

// ── Menu ─────────────────────────────────────────────────────────────────

public record CreateMenuRequest(
        @NotBlank String name,
        @Min(0)   int price,
        @Min(0) @Max(5) int spicyLevel,
        @Min(1)   int cookTime,
        String imageUrl,
        @NotNull  Long categoryId
) {}
