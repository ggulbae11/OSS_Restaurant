package com.restaurant.core.dto;

import com.restaurant.core.domain.Menu;
import com.restaurant.core.domain.Order.OrderStatus;
import com.restaurant.core.domain.Review;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;

public record UpdateStatusRequest(@NotNull OrderStatus status) {}
