package com.ex.final22c.service.refund;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ex.final22c.data.order.Order;
import com.ex.final22c.data.order.OrderDetail;
import com.ex.final22c.data.payment.Payment;
import com.ex.final22c.data.refund.Refund;
import com.ex.final22c.data.refund.RefundDetail;
import com.ex.final22c.data.user.Users;
import com.ex.final22c.repository.order.OrderRepository;
import com.ex.final22c.repository.orderDetail.OrderDetailRepository;
import com.ex.final22c.repository.payment.PaymentRepository;
import com.ex.final22c.repository.refund.RefundRepository;
import com.ex.final22c.repository.user.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RefundService {

    private final RefundRepository refundRepository;
    private final OrderRepository orderRepository;
    private final OrderDetailRepository orderDetailRepository;
    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;

    @Transactional
    public Long requestRefund(long orderId,
                              String principalName,
                              String reasonText,
                              List<Map<String, Object>> items // null 가능
    ) {
        // 1) 사용자
        Users user = userRepository.findByEmail(principalName)
                .or(() -> userRepository.findByUserName(principalName))
                .orElseThrow(() -> new IllegalArgumentException("회원 정보를 찾을 수 없습니다."));

        // 2) 주문
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("주문을 찾을 수 없습니다."));

        if (order.getUser() == null || !order.getUser().getUserNo().equals(user.getUserNo())) {
            throw new IllegalStateException("본인 주문에 대해서만 환불을 요청할 수 있습니다.");
        }
        if (!"PAID".equalsIgnoreCase(order.getStatus())) {
            throw new IllegalStateException("결제 완료 상태에서만 환불 요청이 가능합니다.");
        }
        if (!"DELIVERED".equalsIgnoreCase(order.getDeliveryStatus())) {
            throw new IllegalStateException("배송완료 상태에서만 환불 요청이 가능합니다.");
        }
        if (refundRepository.existsByOrderAndStatus(order, "REQUESTED")) {
            throw new IllegalStateException("이미 처리 중인 환불 요청이 있습니다.");
        }

        // 3) 결제
        Payment payment = paymentRepository.findTopByOrder_OrderIdOrderByPaymentIdDesc(orderId)
                .orElseThrow(() -> new IllegalStateException("주문 결제 정보를 찾을 수 없습니다."));

        // 4) Refund 생성 (reasonText ↦ Refund.reasonText)
        if (reasonText == null || reasonText.isBlank()) {
            throw new IllegalArgumentException("환불 사유를 입력해주세요.");
        }
        Refund refund = new Refund();
        refund.setOrder(order);
        refund.setUser(user);
        refund.setPayment(payment);
        refund.setStatus("REQUESTED");
        refund.setReasonText(reasonText.trim());
        refund.setPgRefundId(null);
        refund.setPgPayloadJson(null);
        refund.setCreateDate(LocalDateTime.now());

        // 5) 부분환불 항목 → RefundDetail 엔티티 생성
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("환불 수량을 1개 이상 선택하세요.");
        }

        // int totalRefund = 0;     변경됨
        int requestedCountSum = 0; // 검증용(금액 아님)

        for (Map<String, Object> m : items) {
            if (m == null) continue;

            Long odId = toLong(m.get("orderDetailId"));
            Integer qty = toInt(m.get("quantity"));
            if (odId == null || qty == null) continue;

            OrderDetail od = orderDetailRepository.findById(odId)
                    .orElseThrow(() -> new IllegalArgumentException("주문상세를 찾을 수 없습니다."));

            // 같은 주문의 상세인지 확인
            if (od.getOrder() == null || od.getOrder().getOrderId() != orderId) {
                throw new IllegalStateException("해당 주문의 상세만 환불할 수 있습니다.");
            }

            int max = od.getQuantity();     // 구매 당시 수량
            if (qty < 0 || qty > max) {
                throw new IllegalArgumentException("환불 수량이 유효하지 않습니다. (0 ~ " + max + ")");
            }
            if (qty == 0) continue;

            // // 단가 계산: 총액/수량 (반올림)
            // int unit = Math.round((float) od.getTotalPrice() / Math.max(1, max));
            // int line = unit * qty;

            // 단가 = 구매 시점 단가(스냅샷). 주문상세에 sellPrice 컬럼이 있다고 가정.
            Integer unit = od.getSellPrice();
            if (unit == null) {
                // 안전장치: 혹시 sellPrice가 없다면 기존 계산식으로 fallback
                unit = Math.round((float) od.getTotalPrice() / Math.max(1, max));
            }

            RefundDetail rd = new RefundDetail();
            rd.setRefund(refund);            // 양방향 연결은 refund.addDetail(rd) 써도 됨
            rd.setOrderDetail(od);

            rd.setQuantity(qty);                            // 환불 신청 수량
            rd.setRefundQty(0);                   // 승인 수량 (초기엔 0)
            rd.setUnitRefundAmount(unit);                   // 구매 단가
            rd.setDetailRefundAmount(qty * unit);           // 승인 금액

            refund.addDetail(rd);            // 리스트 추가 + 역방향 세팅
            // totalRefund += line;
            requestedCountSum += qty;
        }

        if (requestedCountSum <= 0) {
            throw new IllegalArgumentException("환불 수량을 1개 이상 선택하세요.");
        }
        refund.setTotalRefundAmount(0);

        // 6) 저장
        return refundRepository.save(refund).getRefundId();
    }

    private static Long toLong(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.longValue();
        try { return Long.parseLong(String.valueOf(o)); } catch (Exception e) { return null; }
    }
    private static Integer toInt(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.intValue();
        try { return Integer.parseInt(String.valueOf(o)); } catch (Exception e) { return null; }
    }

    // 환불 상세 내역 보기
    @Transactional(readOnly = true)
    public Refund getRefundGraphForAdmin(Long refundId) {
        return refundRepository.findGraphById(refundId)
            .orElseThrow(() -> new IllegalArgumentException("환불 건을 찾을 수 없습니다: " + refundId));
    }
}