package com.ex.final22c.repository.payment;

import com.ex.final22c.data.payment.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByTid(String tid);
    Optional<Payment> findTopByOrder_OrderIdOrderByPaymentIdDesc(Long orderId);
}