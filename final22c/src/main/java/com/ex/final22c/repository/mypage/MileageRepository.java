package com.ex.final22c.repository.mypage;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ex.final22c.data.order.Order;
import com.ex.final22c.data.user.MileageUsageDto;

public interface MileageRepository extends JpaRepository<Order, Long>{
    @Query(value = """
            SELECT *
            FROM (
                -- 1. 사용 내역
                SELECT o.regDate AS date_col,
                       '사용' AS type_col,
                       o.usedPoint AS amount_col,
                       o.orderId AS description_col
                FROM orders o
                WHERE o.userNo = :userId
                  AND o.usedPoint > 0

                UNION ALL

                -- 2. 결제 확정 적립
                SELECT o.regDate AS date_col,
                       '적립' AS type_col,
                       o.confirmMileage AS amount_col,
                       o.orderId AS description_col
                FROM orders o
                WHERE o.userNo = :userId
                  AND o.status = 'CONFIRMED'
                  AND o.confirmMileage > 0

                UNION ALL

                -- 3. 적립
                SELECT r.createDate AS date_col,
                       '환불 건 적립' AS type_col,
                       r.confirmMileage AS amount_col,
                       r.orderId AS description_col
                FROM refund r
                JOIN orders o ON r.orderId = o.orderId
                WHERE o.userNo = :userId
                  AND o.status = 'REFUNDED'
                  AND r.confirmMileage > 0

                UNION ALL

                -- 4. 환불 환급
                SELECT r.createDate AS date_col,
                       '환불 환급' AS type_col,
                       r.refundMileage AS amount_col,
                       r.orderId AS description_col
                FROM refund r
                JOIN orders o ON r.orderId = o.orderId
                WHERE o.userNo = :userId
                  AND o.status = 'REFUNDED'
                  AND r.refundMileage > 0
            )
            ORDER BY date_col DESC
            """, nativeQuery = true)
        List<MileageUsageDto> findMileageUsage(@Param("userId") Long userId);
}
