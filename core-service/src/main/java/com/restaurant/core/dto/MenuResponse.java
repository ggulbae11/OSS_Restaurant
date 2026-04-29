package com.restaurant.core.dto;

import com.restaurant.core.domain.Menu;

public record MenuResponse(
        Long id,
        String name,
        int price,
        String imageUrl,
        int spicyLevel,
        int cookTime,
        boolean available,
        Long categoryId
) {
    public static MenuResponse from(Menu m) {
        return new MenuResponse(
                m.getId(), m.getName(), m.getPrice(),
                m.getImageUrl(), m.getSpicyLevel(), m.getCookTime(),
                m.isAvailable(),
                m.getCategory() != null ? m.getCategory().getId() : null
        );
    }
}
