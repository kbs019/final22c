package com.ex.final22c.repository.orderDetail; 

import com.ex.final22c.data.order.OrderDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderDetailRepository extends JpaRepository<OrderDetail, Long> {
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update OrderDetail d
           set d.confirmQuantity = d.quantity
         where d.order.orderId = :orderId
           and coalesce(d.confirmQuantity, 0) = 0
    """)
    int fillConfirmQtyOnce(@Param("orderId") Long orderId);
}

