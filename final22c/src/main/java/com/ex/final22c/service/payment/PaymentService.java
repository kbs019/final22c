package com.ex.final22c.service.payment;

import com.ex.final22c.data.order.Order;
import com.ex.final22c.data.payment.Payment;
import com.ex.final22c.repository.payment.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;

    /** Ready 단계 저장 (Order FK 필요) */
    public Payment saveReady(Order order, int amount, String tid) {
        Payment p = Payment.builder()
                .order(order)
                .amount(amount)
                .status("READY")
                .tid(tid)
                .build();
        return paymentRepository.save(p);
    }

    /** 승인(Approve) 성공 업데이트 */
    public void markSuccess(String tid, String aid, LocalDateTime approvedAt) {
        Payment p = paymentRepository.findByTid(tid)
                .orElseThrow(() -> new IllegalArgumentException("결제 정보 없음(tid): " + tid));
        p.setAid(aid);
        p.setApprovedAt(approvedAt);
        p.setStatus("SUCCESS");
        paymentRepository.save(p);
    }

    public Payment getByTid(String tid) {
        return paymentRepository.findByTid(tid)
                .orElseThrow(() -> new IllegalArgumentException("결제 정보 없음(tid): " + tid));
    }
    public Payment getLatestByOrderId(Long orderId) {
        return paymentRepository.findTopByOrder_OrderIdOrderByPaymentIdDesc(orderId)
                .orElseThrow(() -> new IllegalArgumentException("결제 정보 없음(orderId): " + orderId));
    }

}
