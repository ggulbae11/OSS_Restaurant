package com.restaurant.core.repository;

import com.restaurant.core.domain.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findByCustomerIdOrderByCreatedAtDesc(Long customerId);

    @Query(value = "SELECT COUNT(*) FROM orders WHERE status IN ('ACCEPTED','COOKING','READY')", nativeQuery = true)
    long countActiveOrders();
}
