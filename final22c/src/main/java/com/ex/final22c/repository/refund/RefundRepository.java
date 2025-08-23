package com.ex.final22c.repository.refund;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ex.final22c.data.order.Order;
import com.ex.final22c.data.refund.Refund;

public interface RefundRepository extends JpaRepository<Refund, Long> {

    /** 해당 주문에 PENDING 상태 환불이 존재하는지 */
    boolean existsByOrderAndStatus(Order order, String status);
}