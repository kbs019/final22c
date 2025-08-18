package com.ex.final22c.service.payment;

import com.ex.final22c.data.order.Order;
import com.ex.final22c.data.payment.Payment;
import com.ex.final22c.repository.payment.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;

    /** Kakao READY 직후 최초 저장 */
    public Payment saveReady(Order order, int amount, String tid) {
        Payment p = Payment.builder()
                .order(order)
                .amount(amount)
                .status("READY")
                .tid(tid)
                .build();
        return paymentRepository.save(p);
    }

    /** 결제 승인 성공 → 결제 레코드만 갱신 (SUCCESS, aid, approvedAt)
     *  마일리지/재고/주문상태는 OrderService.markPaid(...)에서 처리!
     */
    @Transactional
    public void markSuccess(String tid, String aid, LocalDateTime approvedAt) {
        Payment p = paymentRepository.findByTid(tid)
                .orElseThrow(() -> new IllegalArgumentException("결제 정보 없음(tid): " + tid));

        // 이미 처리된 결제면 멱등 처리
        if ("SUCCESS".equalsIgnoreCase(p.getStatus())) return;

        p.setAid(aid);
        p.setApprovedAt(approvedAt);
        p.setStatus("SUCCESS");
        // @Transactional 변경감지로 커밋 시 반영
    }

    /** (선택) 결제 취소되었을 때 Payment 상태만 표시용으로 바꾸고 싶다면 사용 */
    @Transactional
    public void markCanceledByTid(String tid) {
        Payment p = paymentRepository.findByTid(tid)
                .orElseThrow(() -> new IllegalArgumentException("결제 정보 없음(tid): " + tid));
        p.setStatus("CANCELED");
    }

    /** tid 단건 조회 */
    public Payment getByTid(String tid) {
        return paymentRepository.findByTid(tid)
                .orElseThrow(() -> new IllegalArgumentException("결제 정보 없음(tid): " + tid));
    }

    /** 특정 주문의 최신 결제 조회 */
    public Payment getLatestByOrderId(Long orderId) {
        return paymentRepository.findTopByOrder_OrderIdOrderByPaymentIdDesc(orderId)
                .orElseThrow(() -> new IllegalArgumentException("결제 정보 없음(orderId): " + orderId));
    }
}
