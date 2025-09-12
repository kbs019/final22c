package com.ex.final22c.repository.mypage;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ex.final22c.data.order.Order;
import com.ex.final22c.data.user.MileageUsageProjection;

public interface MileageRepository extends JpaRepository<Order, Long>{
	@Query(
		    value = """
		        SELECT
		            o.regDate AS regDate,
		            o.usedPoint AS usedPoint,
		            CASE WHEN o.status = 'CONFIRMED' THEN o.confirmMileage ELSE 0 END AS confirmedMileage,
		            CASE WHEN o.status = 'REFUNDED' THEN r.confirmMileage ELSE 0 END AS refundMileage,
		            CASE WHEN o.status = 'REFUNDED' THEN r.refundMileage ELSE 0 END AS refundRebate,
		            o.orderId AS description
		        FROM orders o
		        LEFT JOIN refund r ON o.orderId = r.orderId
		        WHERE o.userNo = :userId AND o.status != 'FAILED'
		        ORDER BY o.regDate DESC
		        """,
		    nativeQuery = true
		)
		List<MileageUsageProjection> findAllMileageUsageByUserId(@Param("userId") Long userId);
    
    
}
