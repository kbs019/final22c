package com.ex.final22c.repository.orderDetail;

import com.ex.final22c.data.order.OrderDetail;

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

	// confirmQuantity 합계 구함
	@Query("""
				select od.product.id, coalesce(sum(od.confirmQuantity), 0)
				from OrderDetail od
				where od.product.id in :ids
				group by od.product.id
			""")
	List<Object[]> sumConfirmQuantityByProductIds(@Param("ids") Collection<Long> ids);

	@Query("""
           select od
             from OrderDetail od
             join od.product p
             join od.order o
            where p.id = :pid
              and od.confirmQuantity > 0
           """)
    List<OrderDetail> findConfirmedByProductId(@Param("pid") Long productId);
}
