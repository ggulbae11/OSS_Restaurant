package com.restaurant.core.dto;

import com.restaurant.core.domain.Order;
import com.restaurant.core.domain.Order.OrderStatus;
import com.restaurant.core.domain.OrderItem;

import java.time.LocalDateTime;
import java.util.List;

public record OrderResponse(
        Long id,
        OrderStatus status,
        int totalPrice,
        String requestNote,
        Integer estimatedTime,
        LocalDateTime createdAt,
        List<OrderItemResponse> items
) {
    public record OrderItemResponse(
            Long menuId, String menuName, int unitPrice, int quantity, String options, Integer cookTime
    ) {
        public static OrderItemResponse from(OrderItem i) {
            return new OrderItemResponse(
                    i.getMenuId(), i.getMenuName(), i.getUnitPrice(),
                    i.getQuantity(), i.getOptions(), i.getCookTime()
            );
        }
    }

    public static OrderResponse from(Order o) {
        return new OrderResponse(
                o.getId(), o.getStatus(), o.getTotalPrice(),
                o.getRequestNote(), o.getEstimatedTime(), o.getCreatedAt(),
                o.getItems().stream().map(OrderItemResponse::from).toList()
        );
    }
}
