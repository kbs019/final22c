package com.ex.final22c.repository.orderDetail; 

import com.ex.final22c.data.order.OrderDetail;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderDetailRepository extends JpaRepository<OrderDetail, Long> {
	// 1) 결제 승인 직후: quantity = confirmQuantity
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query(value = "UPDATE OrderDetail SET confirmQuantity = quantity WHERE orderId = :orderId",
	       nativeQuery = true)
	int fillConfirmQtyToQuantity(@Param("orderId") Long orderId);
}

