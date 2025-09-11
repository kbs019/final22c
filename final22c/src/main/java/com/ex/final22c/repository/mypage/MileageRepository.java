package com.ex.final22c.repository.mypage;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ex.final22c.data.order.Order;
import com.ex.final22c.data.user.MileageUsageDto;

public interface MileageRepository extends JpaRepository<Order, Long>{
	@Query(value = """
SELECT
    MAX(o.regDate) AS date_col,
    CAST(SUM(CASE WHEN o.usedPoint > 0 THEN o.usedPoint ELSE 0 END) AS NUMBER(10)) AS used_point,
    CAST(SUM(CASE WHEN o.status = 'CONFIRMED' AND o.confirmMileage > 0 THEN o.confirmMileage ELSE 0 END) AS NUMBER(10)) AS confirmed_mileage,
    CAST(SUM(CASE WHEN o.status = 'REFUNDED' THEN r.confirmMileage ELSE 0 END) AS NUMBER(10)) AS refund_mileage,
    CAST(SUM(CASE WHEN o.status = 'REFUNDED' THEN r.refundMileage ELSE 0 END) AS NUMBER(10)) AS refund_rebate,
    o.orderId AS description
FROM orders o
LEFT JOIN refund r ON o.orderId = r.orderId
WHERE o.userNo = :userId
GROUP BY o.orderId
ORDER BY date_col DESC
		    """, nativeQuery = true)
		List<MileageUsageDto> findMileageUsage(@Param("userId") Long userId);
    
    
}
