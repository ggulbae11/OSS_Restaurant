package com.restaurant.core.domain;

import jakarta.persistence.*;
import lombok.*;

/** 메뉴 1개를 만들 때 필요한 재료와 소요량 */
@Entity
@Table(name = "menu_ingredients")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MenuIngredient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "menu_id", nullable = false)
    private Menu menu;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ingredient_id", nullable = false)
    private Ingredient ingredient;

    /** 1인분 기준 소요량 */
    private double requiredAmount;
}
