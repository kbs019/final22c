package com.ex.final22c.repository.purchaseRepository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.ex.final22c.data.purchase.PurchaseDetail;

@Repository
public interface PurchaseDetailRepository extends JpaRepository<PurchaseDetail,Long>{

    // ======================================= 매출 통계 ================================================
    // ----- 타임시리즈: 일/주/월 단위 매입원가 -----
    @Query(value = """
        SELECT TRUNC(pu.REG) AS d,
            SUM(pd.QTY * p.COSTPRICE) AS cogs
        FROM PURCHASEDETAIL pd
        JOIN PURCHASE pu ON pu.PURCHASEID = pd.PURCHASEID
        JOIN PRODUCT  p  ON p.ID = pd.ID
        WHERE pu.REG BETWEEN :from AND :to
        GROUP BY TRUNC(pu.REG)
        ORDER BY d
    """, nativeQuery = true)
    List<Object[]> cogsSeriesByDay(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query(value = """
        SELECT TRUNC(pu.REG, 'IW') AS d,
            SUM(pd.QTY * p.COSTPRICE) AS cogs
        FROM PURCHASEDETAIL pd
        JOIN PURCHASE pu ON pu.PURCHASEID = pd.PURCHASEID
        JOIN PRODUCT  p  ON p.ID = pd.ID
        WHERE pu.REG BETWEEN :from AND :to
        GROUP BY TRUNC(pu.REG, 'IW')
        ORDER BY d
    """, nativeQuery = true)
    List<Object[]> cogsSeriesByWeek(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query(value = """
        SELECT TRUNC(pu.REG, 'MM') AS d,
            SUM(pd.QTY * p.COSTPRICE) AS cogs
        FROM PURCHASEDETAIL pd
        JOIN PURCHASE pu ON pu.PURCHASEID = pd.PURCHASEID
        JOIN PRODUCT  p  ON p.ID = pd.ID
        WHERE pu.REG BETWEEN :from AND :to
        GROUP BY TRUNC(pu.REG, 'MM')
        ORDER BY d
    """, nativeQuery = true)
    List<Object[]> cogsSeriesByMonth(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    // ----- 상품/브랜드별 매입원가 (이미 사용중) -----
    @Query(value = """
        SELECT pd.ID AS product_id,
            SUM(pd.QTY * p.COSTPRICE) AS cogs
        FROM PURCHASEDETAIL pd
        JOIN PURCHASE pu ON pu.PURCHASEID = pd.PURCHASEID
        JOIN PRODUCT  p  ON p.ID = pd.ID
        WHERE pu.REG BETWEEN :from AND :to
        GROUP BY pd.ID
    """, nativeQuery = true)
    List<Object[]> cogsByProduct(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query(value = """
        SELECT p.BRAND_BRANDNO AS brand_no,
            SUM(pd.QTY * p.COSTPRICE) AS cogs
        FROM PURCHASEDETAIL pd
        JOIN PURCHASE pu ON pu.PURCHASEID = pd.PURCHASEID
        JOIN PRODUCT  p  ON p.ID = pd.ID
        WHERE pu.REG BETWEEN :from AND :to
        GROUP BY p.BRAND_BRANDNO
    """, nativeQuery = true)
    List<Object[]> cogsByBrand(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    // 전체 매입원가 합계
    @Query(value = """
        SELECT SUM(pd.QTY * p.COSTPRICE)
        FROM PURCHASEDETAIL pd
        JOIN PURCHASE pu ON pu.PURCHASEID = pd.PURCHASEID
        JOIN PRODUCT  p  ON p.ID = pd.ID
        WHERE pu.REG BETWEEN :from AND :to
    """, nativeQuery = true)
    Long cogsTotal(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query(value = """
    SELECT TRUNC(pu.REG, 'YYYY') AS d,
            SUM(pd.QTY * p.COSTPRICE) AS cogs
    FROM PURCHASEDETAIL pd
    JOIN PURCHASE pu ON pu.PURCHASEID = pd.PURCHASEID
    JOIN PRODUCT  p  ON p.ID = pd.ID
    WHERE pu.REG BETWEEN :from AND :to
    GROUP BY TRUNC(pu.REG, 'YYYY')
    ORDER BY d
    """, nativeQuery = true)
    List<Object[]> cogsSeriesByYear(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    // 상품 이름 포함
    @Query(value = """
    SELECT pd.ID AS product_id, p.NAME AS name,
            SUM(pd.QTY * p.COSTPRICE) AS cogs
    FROM PURCHASEDETAIL pd
    JOIN PURCHASE pu ON pu.PURCHASEID = pd.PURCHASEID
    JOIN PRODUCT  p  ON p.ID = pd.ID
    WHERE pu.REG BETWEEN :from AND :to
        AND (:q IS NULL OR INSTR(p.NAME, :q) > 0)
    GROUP BY pd.ID, p.NAME
    """, nativeQuery = true)
    List<Object[]> cogsByProductWithName(@Param("from") LocalDateTime from,
                                        @Param("to") LocalDateTime to,
                                        @Param("q") String q);

    // 브랜드 이름 포함 (필터 가능)
    @Query(value = """
    SELECT p.BRAND_BRANDNO AS brand_no, b.BRANDNAME AS brand_name,
            SUM(pd.QTY * p.COSTPRICE) AS cogs
    FROM PURCHASEDETAIL pd
    JOIN PURCHASE pu ON pu.PURCHASEID = pd.PURCHASEID
    JOIN PRODUCT  p  ON p.ID = pd.ID
    JOIN BRAND    b  ON b.BRANDNO = p.BRAND_BRANDNO
    WHERE pu.REG BETWEEN :from AND :to
        AND (:brandNo IS NULL OR p.BRAND_BRANDNO = :brandNo)
    GROUP BY p.BRAND_BRANDNO, b.BRANDNAME
    """, nativeQuery = true)
    List<Object[]> cogsByBrandWithName(@Param("from") LocalDateTime from,
                                    @Param("to") LocalDateTime to,
                                    @Param("brandNo") Long brandNo);

// 
    // 상품별 시리즈
    @Query(value = """
    SELECT TRUNC(pu.REG) AS d, SUM(pd.QTY * p.COSTPRICE) AS cogs
    FROM PURCHASEDETAIL pd JOIN PURCHASE pu ON pu.PURCHASEID = pd.PURCHASEID
    JOIN PRODUCT p ON p.ID = pd.ID
    WHERE pd.ID = :productId AND pu.REG BETWEEN :from AND :to
    GROUP BY TRUNC(pu.REG) ORDER BY d
    """, nativeQuery = true)
    List<Object[]> cogsSeriesByDayForProduct(@Param("productId") Long productId, @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query(value = """
    SELECT TRUNC(pu.REG,'IW') AS d, SUM(pd.QTY * p.COSTPRICE) AS cogs
    FROM PURCHASEDETAIL pd JOIN PURCHASE pu ON pu.PURCHASEID = pd.PURCHASEID
    JOIN PRODUCT p ON p.ID = pd.ID
    WHERE pd.ID = :productId AND pu.REG BETWEEN :from AND :to
    GROUP BY TRUNC(pu.REG,'IW') ORDER BY d
    """, nativeQuery = true)
    List<Object[]> cogsSeriesByWeekForProduct(@Param("productId") Long productId, @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query(value = """
    SELECT TRUNC(pu.REG,'MM') AS d, SUM(pd.QTY * p.COSTPRICE) AS cogs
    FROM PURCHASEDETAIL pd JOIN PURCHASE pu ON pu.PURCHASEID = pd.PURCHASEID
    JOIN PRODUCT p ON p.ID = pd.ID
    WHERE pd.ID = :productId AND pu.REG BETWEEN :from AND :to
    GROUP BY TRUNC(pu.REG,'MM') ORDER BY d
    """, nativeQuery = true)
    List<Object[]> cogsSeriesByMonthForProduct(@Param("productId") Long productId, @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query(value = """
    SELECT TRUNC(pu.REG,'YYYY') AS d, SUM(pd.QTY * p.COSTPRICE) AS cogs
    FROM PURCHASEDETAIL pd JOIN PURCHASE pu ON pu.PURCHASEID = pd.PURCHASEID
    JOIN PRODUCT p ON p.ID = pd.ID
    WHERE pd.ID = :productId AND pu.REG BETWEEN :from AND :to
    GROUP BY TRUNC(pu.REG,'YYYY') ORDER BY d
    """, nativeQuery = true)
    List<Object[]> cogsSeriesByYearForProduct(@Param("productId") Long productId, @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    // 브랜드별 시리즈
    @Query(value = """
    SELECT TRUNC(pu.REG) AS d, SUM(pd.QTY * p.COSTPRICE) AS cogs
    FROM PURCHASEDETAIL pd JOIN PURCHASE pu ON pu.PURCHASEID = pd.PURCHASEID
    JOIN PRODUCT p ON p.ID = pd.ID
    WHERE p.BRAND_BRANDNO = :brandNo AND pu.REG BETWEEN :from AND :to
    GROUP BY TRUNC(pu.REG) ORDER BY d
    """, nativeQuery = true)
    List<Object[]> cogsSeriesByDayForBrand(@Param("brandNo") Long brandNo, @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query(value = """
    SELECT TRUNC(pu.REG,'IW') AS d, SUM(pd.QTY * p.COSTPRICE) AS cogs
    FROM PURCHASEDETAIL pd JOIN PURCHASE pu ON pu.PURCHASEID = pd.PURCHASEID
    JOIN PRODUCT p ON p.ID = pd.ID
    WHERE p.BRAND_BRANDNO = :brandNo AND pu.REG BETWEEN :from AND :to
    GROUP BY TRUNC(pu.REG,'IW') ORDER BY d
    """, nativeQuery = true)
    List<Object[]> cogsSeriesByWeekForBrand(@Param("brandNo") Long brandNo, @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query(value = """
    SELECT TRUNC(pu.REG,'MM') AS d, SUM(pd.QTY * p.COSTPRICE) AS cogs
    FROM PURCHASEDETAIL pd JOIN PURCHASE pu ON pu.PURCHASEID = pd.PURCHASEID
    JOIN PRODUCT p ON p.ID = pd.ID
    WHERE p.BRAND_BRANDNO = :brandNo AND pu.REG BETWEEN :from AND :to
    GROUP BY TRUNC(pu.REG,'MM') ORDER BY d
    """, nativeQuery = true)
    List<Object[]> cogsSeriesByMonthForBrand(@Param("brandNo") Long brandNo, @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query(value = """
    SELECT TRUNC(pu.REG,'YYYY') AS d, SUM(pd.QTY * p.COSTPRICE) AS cogs
    FROM PURCHASEDETAIL pd JOIN PURCHASE pu ON pu.PURCHASEID = pd.PURCHASEID
    JOIN PRODUCT p ON p.ID = pd.ID
    WHERE p.BRAND_BRANDNO = :brandNo AND pu.REG BETWEEN :from AND :to
    GROUP BY TRUNC(pu.REG,'YYYY') ORDER BY d
    """, nativeQuery = true)
    List<Object[]> cogsSeriesByYearForBrand(@Param("brandNo") Long brandNo, @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);


    // ===================================================== 23 : 38 =================================================
// --- 상품 시리즈 ---
    @Query(value = """
    SELECT TRUNC(pu.REG) AS d,
            SUM(pd.QTY * p.COSTPRICE) AS cogs
    FROM PURCHASEDETAIL pd
    JOIN PURCHASE pu ON pu.PURCHASEID = pd.PURCHASEID
    JOIN PRODUCT  p  ON p.ID = pd.ID
    WHERE pu.REG BETWEEN :from AND :to
        AND pd.ID = :productId
    GROUP BY TRUNC(pu.REG)
    ORDER BY d
    """, nativeQuery = true)
    List<Object[]> cogsSeriesByDayForProduct(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
                                            @Param("productId") Long productId);

    @Query(value = """
    SELECT TRUNC(pu.REG, 'IW') AS d,
            SUM(pd.QTY * p.COSTPRICE) AS cogs
    FROM PURCHASEDETAIL pd
    JOIN PURCHASE pu ON pu.PURCHASEID = pd.PURCHASEID
    JOIN PRODUCT  p  ON p.ID = pd.ID
    WHERE pu.REG BETWEEN :from AND :to
        AND pd.ID = :productId
    GROUP BY TRUNC(pu.REG, 'IW')
    ORDER BY d
    """, nativeQuery = true)
    List<Object[]> cogsSeriesByWeekForProduct(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
                                            @Param("productId") Long productId);

    @Query(value = """
    SELECT TRUNC(pu.REG, 'MM') AS d,
            SUM(pd.QTY * p.COSTPRICE) AS cogs
    FROM PURCHASEDETAIL pd
    JOIN PURCHASE pu ON pu.PURCHASEID = pd.PURCHASEID
    JOIN PRODUCT  p  ON p.ID = pd.ID
    WHERE pu.REG BETWEEN :from AND :to
        AND pd.ID = :productId
    GROUP BY TRUNC(pu.REG, 'MM')
    ORDER BY d
    """, nativeQuery = true)
    List<Object[]> cogsSeriesByMonthForProduct(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
                                            @Param("productId") Long productId);

    @Query(value = """
    SELECT TRUNC(pu.REG, 'YYYY') AS d,
            SUM(pd.QTY * p.COSTPRICE) AS cogs
    FROM PURCHASEDETAIL pd
    JOIN PURCHASE pu ON pu.PURCHASEID = pd.PURCHASEID
    JOIN PRODUCT  p  ON p.ID = pd.ID
    WHERE pu.REG BETWEEN :from AND :to
        AND pd.ID = :productId
    GROUP BY TRUNC(pu.REG, 'YYYY')
    ORDER BY d
    """, nativeQuery = true)
    List<Object[]> cogsSeriesByYearForProduct(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
                                            @Param("productId") Long productId);

    // --- 브랜드 시리즈 ---
    @Query(value = """
    SELECT TRUNC(pu.REG) AS d,
            SUM(pd.QTY * p.COSTPRICE) AS cogs
    FROM PURCHASEDETAIL pd
    JOIN PURCHASE pu ON pu.PURCHASEID = pd.PURCHASEID
    JOIN PRODUCT  p  ON p.ID = pd.ID
    WHERE pu.REG BETWEEN :from AND :to
        AND p.BRAND_BRANDNO = :brandNo
    GROUP BY TRUNC(pu.REG)
    ORDER BY d
    """, nativeQuery = true)
    List<Object[]> cogsSeriesByDayForBrand(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
                                        @Param("brandNo") Long brandNo);

    @Query(value = """
    SELECT TRUNC(pu.REG, 'IW') AS d,
            SUM(pd.QTY * p.COSTPRICE) AS cogs
    FROM PURCHASEDETAIL pd
    JOIN PURCHASE pu ON pu.PURCHASEID = pd.PURCHASEID
    JOIN PRODUCT  p  ON p.ID = pd.ID
    WHERE pu.REG BETWEEN :from AND :to
        AND p.BRAND_BRANDNO = :brandNo
    GROUP BY TRUNC(pu.REG, 'IW')
    ORDER BY d
    """, nativeQuery = true)
    List<Object[]> cogsSeriesByWeekForBrand(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
                                            @Param("brandNo") Long brandNo);

    @Query(value = """
    SELECT TRUNC(pu.REG, 'MM') AS d,
            SUM(pd.QTY * p.COSTPRICE) AS cogs
    FROM PURCHASEDETAIL pd
    JOIN PURCHASE pu ON pu.PURCHASEID = pd.PURCHASEID
    JOIN PRODUCT  p  ON p.ID = pd.ID
    WHERE pu.REG BETWEEN :from AND :to
        AND p.BRAND_BRANDNO = :brandNo
    GROUP BY TRUNC(pu.REG, 'MM')
    ORDER BY d
    """, nativeQuery = true)
    List<Object[]> cogsSeriesByMonthForBrand(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
                                            @Param("brandNo") Long brandNo);

    @Query(value = """
    SELECT TRUNC(pu.REG, 'YYYY') AS d,
            SUM(pd.QTY * p.COSTPRICE) AS cogs
    FROM PURCHASEDETAIL pd
    JOIN PURCHASE pu ON pu.PURCHASEID = pd.PURCHASEID
    JOIN PRODUCT  p  ON p.ID = pd.ID
    WHERE pu.REG BETWEEN :from AND :to
        AND p.BRAND_BRANDNO = :brandNo
    GROUP BY TRUNC(pu.REG, 'YYYY')
    ORDER BY d
    """, nativeQuery = true)
    List<Object[]> cogsSeriesByYearForBrand(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
                                            @Param("brandNo") Long brandNo);
}
