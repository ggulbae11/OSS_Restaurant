package com.restaurant.core.repository;

import com.restaurant.core.domain.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ReviewRepository extends JpaRepository<Review, Long> {
    List<Review> findByMenuIdAndStatus(Long menuId, Review.ReviewStatus status);
    List<Review> findByMenuId(Long menuId);
}
