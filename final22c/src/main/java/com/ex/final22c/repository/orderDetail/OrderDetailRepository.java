package com.ex.final22c.repository.orderDetail;

import com.ex.final22c.data.order.OrderDetail;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderDetailRepository extends JpaRepository<OrderDetail, Long> {
	// 1) 결제 승인 직후: quantity = confirmQuantity
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query(value = "UPDATE OrderDetail SET confirmQuantity = quantity WHERE orderId = :orderId", nativeQuery = true)
	int fillConfirmQtyToQuantity(@Param("orderId") Long orderId);

	// =============================== 통 계 ========================================

	// confirmQuantity 합계 구함
	// [여러 상품] 전체 누적 확정 수량 (status: PAID/CONFIRMED/REFUNDED)
	@Query("""
			select od.product.id, coalesce(sum(od.confirmQuantity), 0)
			from OrderDetail od
			join od.order o
			where od.product.id in :ids
			  and o.status in :statuses
			group by od.product.id
			""")
	List<Object[]> sumConfirmQuantityByProductIds(
			@Param("ids") Collection<Long> ids,
			@Param("statuses") Collection<String> statuses);

	@Query("""
			select od
			  from OrderDetail od
			  join od.product p
			  join od.order o
			 where p.id = :pid
			   and od.confirmQuantity > 0
			""")
	List<OrderDetail> findConfirmedByProductId(@Param("pid") Long productId);

	// [단일 상품] 전체 누적
	@Query("""
			select coalesce(sum(od.confirmQuantity), 0)
			from OrderDetail od
			join od.order o
			where od.product.id = :id
			  and o.status in :statuses
			""")
	Integer sumAllByProductWithStatuses(
			@Param("id") Long id,
			@Param("statuses") Collection<String> statuses);

	// 범용: 기간 합계
	@Query(value = """
			    SELECT COALESCE(SUM(od.confirmQuantity), 0)
			    FROM orderDetail od
			    JOIN orders o ON o.orderId = od.orderId
			    WHERE od.id = :id
			      AND o.status IN (:statuses)
			      AND o.regDate >= :startAt
			      AND o.regDate <  :endAt
			""", nativeQuery = true)
	Integer sumInRangeWithStatuses(@Param("id") Long id, @Param("statuses") Collection<String> statuses,
			@Param("startAt") LocalDateTime startAt, @Param("endAt") LocalDateTime endAt);

	// 일별
	@Query(value = """
			    SELECT TRUNC(o.regDate) AS k, COALESCE(SUM(od.confirmQuantity), 0) AS qty
			    FROM orderDetail od JOIN orders o ON o.orderId = od.orderId
			    WHERE od.id = :id
			      AND o.status IN (:statuses)
			      AND o.regDate >= :startAt AND o.regDate < :endAt
			    GROUP BY TRUNC(o.regDate)
			    ORDER BY TRUNC(o.regDate)
			""", nativeQuery = true)
	List<Object[]> dailyTotalsWithStatuses(@Param("id") Long id, @Param("statuses") Collection<String> statuses,
			@Param("startAt") LocalDateTime startAt, @Param("endAt") LocalDateTime endAt);

	// 주별(ISO 주 시작: 월요일)
	@Query(value = """
			    SELECT TRUNC(o.regDate, 'IW') AS k, COALESCE(SUM(od.confirmQuantity), 0) AS qty
			    FROM orderDetail od JOIN orders o ON o.orderId = od.orderId
			    WHERE od.id = :id
			      AND o.status IN (:statuses)
			      AND o.regDate >= :startAt AND o.regDate < :endAt
			    GROUP BY TRUNC(o.regDate, 'IW')
			    ORDER BY TRUNC(o.regDate, 'IW')
			""", nativeQuery = true)
	List<Object[]> weeklyTotalsWithStatuses(@Param("id") Long id, @Param("statuses") Collection<String> statuses,
			@Param("startAt") LocalDateTime startAt, @Param("endAt") LocalDateTime endAt);

	// 월별
	@Query(value = """
			    SELECT TRUNC(o.regDate, 'MM') AS k, COALESCE(SUM(od.confirmQuantity), 0) AS qty
			    FROM orderDetail od JOIN orders o ON o.orderId = od.orderId
			    WHERE od.id = :id
			      AND o.status IN (:statuses)
			      AND o.regDate >= :startAt AND o.regDate < :endAt
			    GROUP BY TRUNC(o.regDate, 'MM')
			    ORDER BY TRUNC(o.regDate, 'MM')
			""", nativeQuery = true)
	List<Object[]> monthlyTotalsWithStatuses(@Param("id") Long id, @Param("statuses") Collection<String> statuses,
			@Param("startAt") LocalDateTime startAt, @Param("endAt") LocalDateTime endAt);

	// 연별
	@Query(value = """
			    SELECT TRUNC(o.regDate, 'YYYY') AS k, COALESCE(SUM(od.confirmQuantity), 0) AS qty
			    FROM orderDetail od JOIN orders o ON o.orderId = od.orderId
			    WHERE od.id = :id
			      AND o.status IN (:statuses)
			      AND o.regDate >= :startAt AND o.regDate < :endAt
			    GROUP BY TRUNC(o.regDate, 'YYYY')
			    ORDER BY TRUNC(o.regDate, 'YYYY')
			""", nativeQuery = true)
	List<Object[]> yearlyTotalsWithStatuses(@Param("id") Long id, @Param("statuses") Collection<String> statuses,
			@Param("startAt") LocalDateTime startAt, @Param("endAt") LocalDateTime endAt);
}
