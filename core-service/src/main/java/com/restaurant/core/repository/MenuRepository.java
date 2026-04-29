package com.restaurant.core.repository;

import com.restaurant.core.domain.Menu;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface MenuRepository extends JpaRepository<Menu, Long> {
    List<Menu> findByCategoryIdAndAvailableTrue(Long categoryId);
    List<Menu> findByAvailableTrue();
}
