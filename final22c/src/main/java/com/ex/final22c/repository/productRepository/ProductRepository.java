package com.ex.final22c.repository.productRepository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.ibatis.annotations.Select;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.ex.final22c.data.product.Product;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {
   Page<Product> findAll(Pageable pageable);

   // 재고 1~20 (품절 제외) – 수량 오름차순, id 추가 정렬은 Pageable에서 지정
   Page<Product> findByCountBetween(int min, int max, Pageable pageable);

    // 품절 0
   Page<Product> findByCount(int count, Pageable pageable);

   long countByCountBetween(int min, int max);
   long countByCount(int count);

   // 재고 차감( 현재 재고가 충분할때만 )
   @Modifying(clearAutomatically = true, flushAutomatically = true)
   @Query("""
         update Product p
            set p.count = p.count - :qty
            where p.id = :productId
            and p.count >= :qty
         """)
   int decreaseStock(@Param("productId") Long productId, @Param("qty") int qty);

   // 재고 롤백(추후 취소/환불 구현시)
   @Modifying(clearAutomatically = true, flushAutomatically = true)
   @Query("""
         update Product p
            set p.count = p.count + :qty
            where p.id = :productId
         """)
   int increaseStock(@Param("productId") Long productId, @Param("qty") int qty);

   // 상품목록 (페이징,검색)
   List<Product> findAll(Specification<Product> spec);

   List<Product> findByIsPickedOrderByIdDesc(String isPicked, Pageable pageable);

   // PICK 상품: isPicked == '1' 또는 'Y'
   @EntityGraph(attributePaths = {"brand"})
   @Query("""
         select p
         from Product p
         where p.isPicked in ('1','Y','y','T','true')
         order by p.id desc
         """)
   List<Product> findPicked(Pageable pageable);

   // 집계 프로젝션(상품 + 판매량)
   interface ProductSalesProjection {
      Product getProduct();

      Long getSold(); // alias 'sold' 와 매칭
   }

   // 전체 베스트(판매량 TOP N)
   @Query("""
         select p as product, coalesce(sum(od.confirmQuantity),0) as sold
         from OrderDetail od
         join od.product p
         group by p
         order by sold desc, p.id desc
         """)
   List<ProductSalesProjection> findTopAllBest(Pageable pageable);

   // 성별 베스트(판매량 TOP N)
   @Query("""
            select p as product, coalesce(sum(od.confirmQuantity),0) as sold
            from com.ex.final22c.data.order.OrderDetail od
            join od.product p
            join od.order o
            join o.user u
            where lower(u.gender) = lower(:gender)
            group by p
            order by sold desc, p.id desc
         """)
   List<ProductSalesProjection> findTopByGender(@Param("gender") String gender, Pageable pageable);

   // ================================= 관심 등록 ===============================================
   /** zzimers 컬렉션을 즉시 로딩해서 N+1 방지 */
   @EntityGraph(attributePaths = "zzimers")
   @Query("select p from Product p where p.id = :id")
   Optional<Product> findByIdWithZzimers(@Param("id") Long id);

   /* 사용자(userName)와 상품(productId)의 찜 여부 카운트(0/1) */
	/*
	 * @Query(""" select count(p) from Users u join u.zzimedProducts p where
	 * u.userName = :userName and p.id = :productId """) long
	 * countZzimByUserAndProduct(@Param("userName") String userName,
	 * 
	 * @Param("productId") Long productId);
	 */


   // =============================== 구매자 통계(명수 기준) ===============================

   // 프로젝션 (각 그룹의 "구매자 수" = 사용자 수)
   interface BuyerStatProjection {
      String getGender();     // "M" / "F" / null
      String getAgeBucket();  // "10대"~"50대","기타"
      Long getCnt();          // 구매자 수 (distinct user)
   }

   // 상품별 구매자 통계 (확정 수량 > 0 인 주문라인 존재 && 사용자 중복 제거)
   @Query("""
      select
         u.gender as gender,
         case
            when u.age between 10 and 19 then '10대'
            when u.age between 20 and 29 then '20대'
            when u.age between 30 and 39 then '30대'
            when u.age between 40 and 49 then '40대'
            when u.age between 50 and 59 then '50대'
            else '기타'
         end as ageBucket,
         count(distinct u.userNo) as cnt
      from com.ex.final22c.data.order.OrderDetail od
         join od.order o
         join o.user u
      where od.product.id = :productId
        and od.confirmQuantity > 0
      group by u.gender,
         case
            when u.age between 10 and 19 then '10대'
            when u.age between 20 and 29 then '20대'
            when u.age between 30 and 39 then '30대'
            when u.age between 40 and 49 then '40대'
            when u.age between 50 and 59 then '50대'
            else '기타'
         end
      order by ageBucket
      """)
   List<BuyerStatProjection> findBuyerStatsByProduct(@Param("productId") Long productId);
   
// 상품 상세 정보 조회 (ID로)
   @Query(value = """
		    SELECT p.id as id, p.name as name, p.sellPrice as sellPrice, p.imgName as imgName, p.count as count,
		           b.brandName as brandName, m.mainNoteName as mainNoteName, g.gradeName as gradeName, v.volumeName as volumeName
		    FROM Product p
		    LEFT JOIN p.brand b
		    LEFT JOIN p.mainNote m
		    LEFT JOIN p.grade g
		    LEFT JOIN p.volume v
		    WHERE p.id = :id
		""")
		Map<String, Object> selectProductById(@Param("id") Long id);

   
   // =================== 상품 상세 페이지에서의 상품 추천 캐러셀 ===========================================
   // (A) 같은 브랜드: 판매량 내림차순, 자기 자신 제외, ACTIVE, 재고>0
   @Query(value = """
   SELECT id, name, brandName, imgPath, imgName, listPrice, sellPrice, sold FROM (
   SELECT
      p.id                         AS id,
      p.name                       AS name,
      b.brandName                  AS brandName,
      p.imgPath                    AS imgPath,
      p.imgName                    AS imgName,
      p.price                      AS listPrice,
      p.sellPrice                  AS sellPrice,
      NVL(s.sold, 0)               AS sold,
      ROW_NUMBER() OVER(ORDER BY NVL(s.sold,0) DESC, p.id DESC) AS rn
   FROM Product p
   JOIN Brand b ON b.brandNo = p.brand_brandNo
   LEFT JOIN (
      SELECT od.id AS pid, SUM(od.confirmQuantity) AS sold
      FROM orderDetail od
      GROUP BY od.id
   ) s ON s.pid = p.id
   WHERE p.brand_brandNo = :brandId
      AND p.id <> :productId
      AND p.status = 'active'
      AND p.count  > 0
   )
   WHERE rn <= :limit
   """, nativeQuery = true)
   List<Object[]> findSameBrandSoldDesc(
   @Param("brandId") Long brandId,
   @Param("productId") Long productId,
   @Param("limit") int limit
   );

   // 최근 7일간 판매량 기준 top N
   @Query(value = """
   SELECT id, name, brandName, imgPath, imgName, listPrice, sellPrice, sold FROM (
   SELECT
      p.id                         AS id,
      p.name                       AS name,
      b.brandName                  AS brandName,
      p.imgPath                    AS imgPath,
      p.imgName                    AS imgName,
      p.price                      AS listPrice,
      p.sellPrice                  AS sellPrice,
      SUM(od.confirmQuantity)      AS sold,
      ROW_NUMBER() OVER(ORDER BY SUM(od.confirmQuantity) DESC, p.id DESC) AS rn
   FROM Product p
   JOIN Brand b        ON b.brandNo = p.brand_brandNo
   JOIN orderDetail od ON od.id = p.id
   JOIN Orders o       ON o.orderId = od.orderId
   WHERE p.status = 'active'
      AND p.count  > 0
      AND o.regDate >= TRUNC(SYSDATE) - 6
      AND o.regDate <  TRUNC(SYSDATE) + 1
      AND (:excludeId IS NULL OR p.id <> :excludeId)
      AND o.status IN ('PAID','CONFIRMED','REFUNDED')
   GROUP BY p.id, p.name, b.brandName, p.imgPath, p.imgName, p.price, p.sellPrice
   )
   WHERE rn <= :limit
   """, nativeQuery = true)
   List<Object[]> findRecent7DaysTopSold(@Param("limit") int limit, @Param("excludeId") Long excludeId);

   // 최근 7일이 비면 쓸 누적 베스트 (현재 상품 제외 가능)
   @Query(value = """
   SELECT id, name, brandName, imgPath, imgName, listPrice, sellPrice, sold FROM (
   SELECT
      p.id                         AS id,
      p.name                       AS name,
      b.brandName                  AS brandName,
      p.imgPath                    AS imgPath,
      p.imgName                    AS imgName,
      p.price                      AS listPrice,
      p.sellPrice                  AS sellPrice,
      SUM(od.confirmQuantity)      AS sold,
      ROW_NUMBER() OVER(ORDER BY SUM(od.confirmQuantity) DESC, p.id DESC) AS rn
   FROM Product p
   JOIN Brand b        ON b.brandNo = p.brand_brandNo
   JOIN orderDetail od ON od.id = p.id
   JOIN Orders o       ON o.orderId = od.orderId
   WHERE p.status = 'active'
      AND p.count  > 0
      AND (:excludeId IS NULL OR p.id <> :excludeId)
      AND o.status IN ('PAID','CONFIRMED','REFUNDED')
   GROUP BY p.id, p.name, b.brandName, p.imgPath, p.imgName, p.price, p.sellPrice
   )
   WHERE rn <= :limit
   """, nativeQuery = true)
   List<Object[]> findAllTimeTopSold(@Param("limit") int limit, @Param("excludeId") Long excludeId);
}
