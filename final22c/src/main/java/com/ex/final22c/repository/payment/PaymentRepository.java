package com.ex.final22c.repository.payment;

import com.ex.final22c.data.payment.Payment;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
	// tid 중복 가능성 대비: 단건 금지
	Optional<Payment> findTopByTidOrderByPaymentIdDesc(String tid);
	List<Payment> findAllByTid(String tid);

	Optional<Payment> findTopByOrder_OrderIdOrderByPaymentIdDesc(Long orderId);

	@EntityGraph(attributePaths = {"order", "order.details", "order.details.product"})
	Optional<Payment> findWithOrderAndDetailsByTid(String tid);

	List<Payment> findByOrder_OrderId(Long orderId);

	boolean existsByOrder_OrderIdAndStatus(Long orderId, String status);
    
    
}