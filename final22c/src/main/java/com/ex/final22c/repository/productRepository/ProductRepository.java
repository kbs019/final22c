package com.ex.final22c.repository.productRepository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.ex.final22c.data.product.Product;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {
	
	// 재고 차감( 현재 재고가 충분할때만 ) 
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("""
			update Product p
				set p.count = p.count - :qty
				where p.id = :productId
				and p.count >= :qty
			""")
	int decreaseStock(@Param("productId") Long productId,@Param("qty") int qty);
	
	/*
	// 재고 롤백(추후 취소/환불 구현시)
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("""
			update product p
				set p.count = p.count + :qty
				where p.id = :productId
			""")
	int increaseStock(@Param("productId") Long productId, @Param("qty") int qty);
	*/
}
