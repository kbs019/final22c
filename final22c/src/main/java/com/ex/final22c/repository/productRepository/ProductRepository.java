package com.ex.final22c.repository.productRepository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
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
	
	// 재고 차감( 현재 재고가 충분할때만 ) 
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("""
			update Product p
				set p.count = p.count - :qty
				where p.id = :productId
				and p.count >= :qty
			""")
	int decreaseStock(@Param("productId") Long productId,@Param("qty") int qty);
	
	
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
        Long getSold();   // alias 'sold' 와 매칭
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
}
