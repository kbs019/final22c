package com.ex.final22c.repository.payment;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.ex.final22c.data.payment.Payment;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByTid(String tid);
    Optional<Payment> findTopByOrder_OrderIdOrderByPaymentIdDesc(Long orderId);
    
    @EntityGraph(attributePaths = {"order", "order.details", "order.details.product"})
    Optional<Payment> findWithOrderAndDetailsByTid(String tid);
    List<Payment> findByOrder_OrderId(Long orderId);
    Optional<Payment> findTopByOrderOrderId(Long orderId);
}