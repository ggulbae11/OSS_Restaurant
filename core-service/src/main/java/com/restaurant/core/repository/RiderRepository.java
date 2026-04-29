package com.restaurant.core.repository;

import com.restaurant.core.domain.Rider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.Optional;

public interface RiderRepository extends JpaRepository<Rider, Long> {

    // 대기 중인 라이더 중 마지막 배달 시간 오래된 순 → 총 배달 수 적은 순
    @Query(value = """
            SELECT * FROM riders
            WHERE status = 'WAITING'
            ORDER BY last_assigned_at ASC NULLS FIRST,
                     total_delivery_count ASC
            LIMIT 1
            """, nativeQuery = true)
    Optional<Rider> findFirstAvailableRider();
}
