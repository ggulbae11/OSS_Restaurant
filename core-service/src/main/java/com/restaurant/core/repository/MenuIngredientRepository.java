package com.restaurant.core.repository;

import com.restaurant.core.domain.MenuIngredient;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface MenuIngredientRepository extends JpaRepository<MenuIngredient, Long> {
    List<MenuIngredient> findByMenu_Id(Long menuId);
}
