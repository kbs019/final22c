package com.ex.final22c.repository.refund;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.ex.final22c.data.order.Order;
import com.ex.final22c.data.refund.Refund;

@Repository
public interface RefundRepository extends JpaRepository<Refund, Long> {
    List<Refund> findAllByOrderByCreateDateDesc();

    /** 해당 주문에 REQUESTED 상태 환불이 존재하는지 */
    boolean existsByOrderAndStatus(Order order, String status);
}