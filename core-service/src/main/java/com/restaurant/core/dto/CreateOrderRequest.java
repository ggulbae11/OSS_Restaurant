package com.restaurant.core.dto;

import com.restaurant.core.domain.Order;
import com.restaurant.core.domain.Order.OrderStatus;
import com.restaurant.core.domain.OrderItem;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.LocalDateTime;
import java.util.List;

public record CreateOrderRequest(
        @NotEmpty @Valid List<OrderItemRequest> items,
        String requestNote
) {
    public record OrderItemRequest(
            @NotNull Long menuId,
            @Min(1)  int quantity,
            List<String> options
    ) {}
}
