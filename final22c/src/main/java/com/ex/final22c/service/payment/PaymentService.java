package com.ex.final22c.service.payment;

import com.ex.final22c.data.order.Order;
import com.ex.final22c.data.payment.Payment;
import com.ex.final22c.repository.payment.PaymentRepository;
import com.ex.final22c.service.order.OrderService;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final OrderService orderService;

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
     *  마일리지/재고/주문상태는 OrderService.markPaid(...)에서 처리
     */
    @Transactional
    public void markSuccess(String tid, String aid, LocalDateTime approvedAt) {
        if (tid == null || tid.isBlank()) {
            throw new IllegalArgumentException("tid is null/blank");
        }
        Payment p = paymentRepository.findTopByTidOrderByPaymentIdDesc(tid)
                .orElseThrow(() -> new IllegalArgumentException("결제 정보 없음(tid): " + tid));

        // 이미 성공 처리된 결제면 멱등 처리
        if ("SUCCESS".equalsIgnoreCase(p.getStatus())) return;

        p.setAid(aid);
        p.setApprovedAt(approvedAt);
        p.setStatus("SUCCESS"); // 또는 "APPROVED" 프로젝트 표준에 맞추세요
    }

    /** (선택) 결제 취소되었을 때 Payment 상태만 표시용으로 바꾸고 싶다면 사용 */
    @Transactional
    public void markCanceledByTid(String tid) {
        if (tid == null || tid.isBlank()) {
            throw new IllegalArgumentException("tid is null/blank");
        }
        Payment p = paymentRepository.findTopByTidOrderByPaymentIdDesc(tid)
                .orElseThrow(() -> new IllegalArgumentException("결제 정보 없음(tid): " + tid));
        p.setStatus("CANCELED");
        p.setApprovedAt(LocalDateTime.now()); // 취소 시각으로 업데이트
    }
    
    /** tid 단건 조회 */
    public Payment getByTid(String tid) {
        if (tid == null || tid.isBlank()) {
            throw new IllegalArgumentException("tid is null/blank");
        }
        return paymentRepository.findTopByTidOrderByPaymentIdDesc(tid)
                .orElseThrow(() -> new IllegalArgumentException("결제 정보 없음(tid): " + tid));
    }
    
    /** 특정 주문의 최신 결제 조회 */
    public Payment getLatestByOrderId(Long orderId) {
        return paymentRepository.findTopByOrder_OrderIdOrderByPaymentIdDesc(orderId)
                .orElseThrow(() -> new IllegalArgumentException("결제 정보 없음(orderId): " + orderId));
    }
    
    @Transactional
    public void recordZeroPayment(Long orderId) {
        // 이미 승인 결제가 있으면 스킵 (멱등성)
        if (paymentRepository.existsByOrder_OrderIdAndStatus(orderId, "APPROVED")) return;

        Order order = orderService.get(orderId);
        if (order == null) throw new IllegalArgumentException("order not found: " + orderId);

        Payment p = Payment.builder()
                .order(order)
                .amount(0)                 // 0원
                .status("SUCCESS")        // 성공 처리
                .tid(null)                 // PG 미사용
                .aid(null)
                .build();
        paymentRepository.save(p);
    }
    /** 0원 결제 취소일때 tid 말고 orderId기반 **/
    @Transactional
    public void markCanceledByOrderId(Long orderId) {
        Payment latest = paymentRepository.findTopByOrder_OrderIdOrderByPaymentIdDesc(orderId)
                .orElseThrow(() -> new IllegalStateException("payment not found by orderId: " + orderId));
        latest.setStatus("CANCELED");
        latest.setApprovedAt(LocalDateTime.now());
    }
}
