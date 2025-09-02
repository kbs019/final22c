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

	

	// ======================================== 매출 통계 =============================================
	// ----- 타임시리즈: 일/주/월 단위 순매출 -----
	@Query(value = """
		SELECT TRUNC(o.REGDATE) AS d,
			SUM(NVL(od.CONFIRMQUANTITY,0) * od.SELLPRICE) AS revenue
		FROM ORDERDETAIL od
		JOIN ORDERS o ON o.ORDERID = od.ORDERID
		WHERE o.STATUS IN ('PAID','CONFIRMED','REFUNDED')
		AND o.REGDATE BETWEEN :from AND :to
		GROUP BY TRUNC(o.REGDATE)
		ORDER BY d
	""", nativeQuery = true)
	List<Object[]> revenueSeriesByDay(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

	@Query(value = """
		SELECT TRUNC(o.REGDATE, 'IW') AS d,
			SUM(NVL(od.CONFIRMQUANTITY,0) * od.SELLPRICE) AS revenue
		FROM ORDERDETAIL od
		JOIN ORDERS o ON o.ORDERID = od.ORDERID
		WHERE o.STATUS IN ('PAID','CONFIRMED','REFUNDED')
		AND o.REGDATE BETWEEN :from AND :to
		GROUP BY TRUNC(o.REGDATE, 'IW')
		ORDER BY d
	""", nativeQuery = true)
	List<Object[]> revenueSeriesByWeek(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

	@Query(value = """
		SELECT TRUNC(o.REGDATE, 'MM') AS d,
			SUM(NVL(od.CONFIRMQUANTITY,0) * od.SELLPRICE) AS revenue
		FROM ORDERDETAIL od
		JOIN ORDERS o ON o.ORDERID = od.ORDERID
		WHERE o.STATUS IN ('PAID','CONFIRMED','REFUNDED')
		AND o.REGDATE BETWEEN :from AND :to
		GROUP BY TRUNC(o.REGDATE, 'MM')
		ORDER BY d
	""", nativeQuery = true)
	List<Object[]> revenueSeriesByMonth(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

	@Query(value = """
	SELECT TRUNC(o.REGDATE, 'YYYY') AS d,
			SUM(NVL(od.CONFIRMQUANTITY,0) * od.SELLPRICE) AS revenue
	FROM ORDERDETAIL od
	JOIN ORDERS o ON o.ORDERID = od.ORDERID
	WHERE o.STATUS IN ('PAID','CONFIRMED','REFUNDED')
		AND o.REGDATE BETWEEN :from AND :to
	GROUP BY TRUNC(o.REGDATE, 'YYYY')
	ORDER BY d
	""", nativeQuery = true)
	List<Object[]> revenueSeriesByYear(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

	// --- 상품 시리즈 ---
	@Query(value = """
	SELECT TRUNC(o.REGDATE) AS d,
			SUM(NVL(od.CONFIRMQUANTITY,0) * od.SELLPRICE) AS revenue
	FROM ORDERDETAIL od
	JOIN ORDERS o ON o.ORDERID = od.ORDERID
	WHERE o.STATUS IN ('PAID','CONFIRMED','REFUNDED')
		AND o.REGDATE BETWEEN :from AND :to
		AND od.ID = :productId
	GROUP BY TRUNC(o.REGDATE)
	ORDER BY d
	""", nativeQuery = true)
	List<Object[]> revenueSeriesByDayForProduct(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
												@Param("productId") Long productId);

	@Query(value = """
	SELECT TRUNC(o.REGDATE,'IW') AS d,
			SUM(NVL(od.CONFIRMQUANTITY,0) * od.SELLPRICE) AS revenue
	FROM ORDERDETAIL od
	JOIN ORDERS o ON o.ORDERID = od.ORDERID
	WHERE o.STATUS IN ('PAID','CONFIRMED','REFUNDED')
		AND o.REGDATE BETWEEN :from AND :to
		AND od.ID = :productId
	GROUP BY TRUNC(o.REGDATE,'IW')
	ORDER BY d
	""", nativeQuery = true)
	List<Object[]> revenueSeriesByWeekForProduct(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
												@Param("productId") Long productId);

	@Query(value = """
	SELECT TRUNC(o.REGDATE,'MM') AS d,
			SUM(NVL(od.CONFIRMQUANTITY,0) * od.SELLPRICE) AS revenue
	FROM ORDERDETAIL od
	JOIN ORDERS o ON o.ORDERID = od.ORDERID
	WHERE o.STATUS IN ('PAID','CONFIRMED','REFUNDED')
		AND o.REGDATE BETWEEN :from AND :to
		AND od.ID = :productId
	GROUP BY TRUNC(o.REGDATE,'MM')
	ORDER BY d
	""", nativeQuery = true)
	List<Object[]> revenueSeriesByMonthForProduct(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
												@Param("productId") Long productId);

	@Query(value = """
	SELECT TRUNC(o.REGDATE,'YYYY') AS d,
			SUM(NVL(od.CONFIRMQUANTITY,0) * od.SELLPRICE) AS revenue
	FROM ORDERDETAIL od
	JOIN ORDERS o ON o.ORDERID = od.ORDERID
	WHERE o.STATUS IN ('PAID','CONFIRMED','REFUNDED')
		AND o.REGDATE BETWEEN :from AND :to
		AND od.ID = :productId
	GROUP BY TRUNC(o.REGDATE,'YYYY')
	ORDER BY d
	""", nativeQuery = true)
	List<Object[]> revenueSeriesByYearForProduct(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
												@Param("productId") Long productId);

	// --- 브랜드 시리즈 ---
	@Query(value = """
	SELECT TRUNC(o.REGDATE) AS d,
			SUM(NVL(od.CONFIRMQUANTITY,0) * od.SELLPRICE) AS revenue
	FROM ORDERDETAIL od
	JOIN ORDERS o  ON o.ORDERID = od.ORDERID
	JOIN PRODUCT p ON p.ID = od.ID
	WHERE o.STATUS IN ('PAID','CONFIRMED','REFUNDED')
		AND o.REGDATE BETWEEN :from AND :to
		AND p.BRAND_BRANDNO = :brandNo
	GROUP BY TRUNC(o.REGDATE)
	ORDER BY d
	""", nativeQuery = true)
	List<Object[]> revenueSeriesByDayForBrand(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
											@Param("brandNo") Long brandNo);

	@Query(value = """
	SELECT TRUNC(o.REGDATE, 'IW') AS d,
			SUM(NVL(od.CONFIRMQUANTITY,0) * od.SELLPRICE) AS revenue
	FROM ORDERDETAIL od
	JOIN ORDERS o  ON o.ORDERID = od.ORDERID
	JOIN PRODUCT p ON p.ID = od.ID
	WHERE o.STATUS IN ('PAID','CONFIRMED','REFUNDED')
		AND o.REGDATE BETWEEN :from AND :to
		AND p.BRAND_BRANDNO = :brandNo
	GROUP BY TRUNC(o.REGDATE, 'IW')
	ORDER BY d
	""", nativeQuery = true)
	List<Object[]> revenueSeriesByWeekForBrand(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
											@Param("brandNo") Long brandNo);

	@Query(value = """
	SELECT TRUNC(o.REGDATE, 'MM') AS d,
			SUM(NVL(od.CONFIRMQUANTITY,0) * od.SELLPRICE) AS revenue
	FROM ORDERDETAIL od
	JOIN ORDERS o  ON o.ORDERID = od.ORDERID
	JOIN PRODUCT p ON p.ID = od.ID
	WHERE o.STATUS IN ('PAID','CONFIRMED','REFUNDED')
		AND o.REGDATE BETWEEN :from AND :to
		AND p.BRAND_BRANDNO = :brandNo
	GROUP BY TRUNC(o.REGDATE, 'MM')
	ORDER BY d
	""", nativeQuery = true)
	List<Object[]> revenueSeriesByMonthForBrand(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
												@Param("brandNo") Long brandNo);

	@Query(value = """
	SELECT TRUNC(o.REGDATE, 'YYYY') AS d,
			SUM(NVL(od.CONFIRMQUANTITY,0) * od.SELLPRICE) AS revenue
	FROM ORDERDETAIL od
	JOIN ORDERS o  ON o.ORDERID = od.ORDERID
	JOIN PRODUCT p ON p.ID = od.ID
	WHERE o.STATUS IN ('PAID','CONFIRMED','REFUNDED')
		AND o.REGDATE BETWEEN :from AND :to
		AND p.BRAND_BRANDNO = :brandNo
	GROUP BY TRUNC(o.REGDATE, 'YYYY')
	ORDER BY d
	""", nativeQuery = true)
	List<Object[]> revenueSeriesByYearForBrand(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
											@Param("brandNo") Long brandNo);

	// =========================== 브랜드 옵션 =====================================
	// 브랜드 옵션
	@Query(value = """
	SELECT b.BRANDNO AS id, b.BRANDNAME AS name
	FROM BRAND b
	ORDER BY b.BRANDNAME
	""", nativeQuery = true)
	List<Object[]> brandOptions();

	// 브랜드의 용량들(중복 제거)
	@Query(value = """
	SELECT DISTINCT v.VOLUMENAME
	FROM PRODUCT p
	JOIN VOLUME v ON v.VOLUMENO = p.VOLUME_VOLUMENO
	WHERE p.BRAND_BRANDNO = :brandNo
	ORDER BY v.VOLUMENAME
	""", nativeQuery = true)
	List<String> capacitiesByBrand(@Param("brandNo") Long brandNo);

	// 브랜드+용량의 상품 목록
	@Query(value = """
	SELECT p.ID, p.NAME
	FROM PRODUCT p
	JOIN VOLUME v ON v.VOLUMENO = p.VOLUME_VOLUMENO
	WHERE p.BRAND_BRANDNO = :brandNo
		AND v.VOLUMENAME = :capacity
	ORDER BY p.NAME
	""", nativeQuery = true)
	List<Object[]> productsByBrandCapacity(@Param("brandNo") Long brandNo, @Param("capacity") String capacity);
}
