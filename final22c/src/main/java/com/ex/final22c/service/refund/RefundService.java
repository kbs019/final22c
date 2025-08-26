package com.ex.final22c.service.refund;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ex.final22c.data.order.Order;
import com.ex.final22c.data.order.OrderDetail;
import com.ex.final22c.data.payment.Payment;
import com.ex.final22c.data.refund.ApproveRefundRequest;
import com.ex.final22c.data.refund.ApproveRefundResult;
import com.ex.final22c.data.refund.Refund;
import com.ex.final22c.data.refund.RefundDetail;
import com.ex.final22c.data.user.Users;
import com.ex.final22c.repository.order.OrderRepository;
import com.ex.final22c.repository.orderDetail.OrderDetailRepository;
import com.ex.final22c.repository.payment.PaymentRepository;
import com.ex.final22c.repository.refund.RefundRepository;
import com.ex.final22c.repository.refundDetail.RefundDetailRepository;
import com.ex.final22c.repository.user.UserRepository;
import com.ex.final22c.service.KakaoApiService;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RefundService {

    private final RefundRepository refundRepository;
    private final RefundDetailRepository refundDetailRepository;
    private final OrderRepository orderRepository;
    private final OrderDetailRepository orderDetailRepository;
    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;

    private final KakaoApiService kakaoApiService;
    private final RefundSmsService refundSmsService;
    private final ObjectMapper objectMapper;

    private static final int FIXED_SHIPPING_REFUND = 3_000;

    // 환불 상세 내역 보기
    @Transactional(readOnly = true)
    public Refund getRefundGraphForAdmin(Long refundId) {
        return refundRepository.findGraphByRefundId(refundId)
                .orElseThrow(() -> new IllegalArgumentException("환불 건을 찾을 수 없습니다: " + refundId));
    }

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
        Payment payment = paymentRepository.findTopByOrderOrderId(orderId)
                // .or(() -> paymentRepository.findTopByOrderId(orderId)) // 매핑에 맞게
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
        refund.setRequestedReason(reasonText.trim());
        refund.setPgRefundId(null);
        refund.setPgPayloadJson(null);
        refund.setCreateDate(LocalDateTime.now());

        // 5) 부분환불 항목 → RefundDetail 엔티티 생성
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("환불 수량을 1개 이상 선택하세요.");
        }

        // int totalRefund = 0; 변경됨
        int requestedCountSum = 0; // 검증용(금액 아님)

        for (Map<String, Object> m : items) {
            if (m == null)
                continue;

            Long odId = toLong(m.get("orderDetailId"));
            Integer qty = toInt(m.get("quantity"));
            if (odId == null || qty == null)
                continue;

            OrderDetail od = orderDetailRepository.findById(odId)
                    .orElseThrow(() -> new IllegalArgumentException("주문상세를 찾을 수 없습니다."));

            // 같은 주문의 상세인지 확인
            if (od.getOrder() == null || od.getOrder().getOrderId() != orderId) {
                throw new IllegalStateException("해당 주문의 상세만 환불할 수 있습니다.");
            }

            int max = od.getQuantity(); // 구매 당시 수량
            if (qty < 0 || qty > max) {
                throw new IllegalArgumentException("환불 수량이 유효하지 않습니다. (0 ~ " + max + ")");
            }
            if (qty == 0)
                continue;

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
            rd.setRefund(refund); // 양방향 연결은 refund.addDetail(rd) 써도 됨
            rd.setOrderDetail(od);

            rd.setQuantity(qty); // 환불 신청 수량
            rd.setRefundQty(0); // 승인 수량 (초기엔 0)
            rd.setUnitRefundAmount(unit); // 구매 단가
            rd.setDetailRefundAmount(qty * unit); // 승인 금액

            refund.addDetail(rd); // 리스트 추가 + 역방향 세팅
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
        if (o == null)
            return null;
        if (o instanceof Number n)
            return n.longValue();
        try {
            return Long.parseLong(String.valueOf(o));
        } catch (Exception e) {
            return null;
        }
    }

    private static Integer toInt(Object o) {
        if (o == null)
            return null;
        if (o instanceof Number n)
            return n.intValue();
        try {
            return Integer.parseInt(String.valueOf(o));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 환불 승인 처리
     * - 상세별: refundQty 반영, detailRefundAmount = refundQty * unitRefundAmount
     * - 상위합: refund.totalRefundAmount = ∑detailRefundAmount
     * - 거절수량( quantity - refundQty ) → orderDetail.confirmQuantity += ...
     * - PG 환불(총액)
     * - refund.status = REFUNDED, order.status = REFUNDED
     * - 문자 발송(거절수량 존재 시 rejectMessage 포함)
     */
    @Transactional
    public ApproveRefundResult approveRefund(ApproveRefundRequest req, String approver) {

        // 1) 환불건 로딩 (order, payment, details, details.orderDetail)
        Refund refund = refundRepository.findByRefundId(req.getRefundId()).orElse(null);
        if (refund == null) {
            return ApproveRefundResult.error("환불건이 존재하지 않습니다: " + req.getRefundId());
        }
        if (!"REQUESTED".equalsIgnoreCase(safe(refund.getStatus()))) {
            return ApproveRefundResult.error("이미 처리된 환불건입니다.");
        }

        Order order = refund.getOrder();                // 상태 표시/문자용
        Payment payment = refund.getPayment();          // PG 환불용

        // 2) 입력 맵 구성
        Map<Long, Integer> inputQtyMap = Optional.ofNullable(req.getItems())
                .orElseGet(Collections::emptyList)
                .stream()
                .collect(Collectors.toMap(
                        ApproveRefundRequest.Item::getRefundDetailId,
                        i -> nvl(i.getRefundQty(), 0),
                        (a, b) -> b
                ));

        // 3) 상세 계산: 승인/거절 수량 및 금액, 승인/거절 "건수"
        int itemSubtotal = 0;     // ★ 상품 소계(단가×승인수량 합)
        int approvedLines = 0;
        int rejectedLines = 0;
        int rejectedQtyTotal = 0; // ★ 총 거절 수량
        int approvedQtyTotal = 0; // ★ 승인 "총 수량" 

        for (RefundDetail d : refund.getDetails()) {
            int requested = nvl(d.getQuantity(), 0);
            int reqApprove = nvl(inputQtyMap.get(d.getRefundDetailId()), 0);
            int approveQty = clamp(reqApprove, 0, requested);

            d.setRefundQty(approveQty);

            int lineAmount = approveQty * nvl(d.getUnitRefundAmount(), 0);
            d.setDetailRefundAmount(lineAmount);
            itemSubtotal += lineAmount;

            approvedQtyTotal += Math.max(0, approveQty);
            int rejectedQty = requested - approveQty;
            rejectedQtyTotal += Math.max(0, rejectedQty);

            if (approveQty > 0)
                approvedLines++;
            if (rejectedQty > 0) {
                rejectedLines++;
                OrderDetail od = d.getOrderDetail();
                od.setConfirmQuantity(nvl(od.getConfirmQuantity(), 0) - approveQty); // confirmQuantity 를 승인 수량만큼 다운시켜서 거절된 수량은 주문확정된 건으로 처리
                orderDetailRepository.save(od);
            }
            refundDetailRepository.save(d);
        }

        boolean partial = (rejectedQtyTotal > 0);      // 부분환불 true / 전체환불 false

        // 4) 부분 환불이면 거절사유 필수(요청 레벨 단일 문자열)
        String rejectReason = nullIfBlank(req.getRejectionReason());
        if ( partial && rejectReason == null ) {
            return ApproveRefundResult.error("부분 환불 시 거절 사유는 필수입니다.");
        }

        // 5) PG 환불 (총액 > 0이면 필수)
        int shippingRefund = (approvedQtyTotal > 0) ? FIXED_SHIPPING_REFUND : 0;
        int finalRefundAmount = itemSubtotal + shippingRefund;

        String pgRefundId = null;
        String pgPayloadJson = null;

        if (finalRefundAmount > 0) {
            if (payment == null || payment.getTid() == null || payment.getTid().isBlank()) {
                return ApproveRefundResult.error("환불에 필요한 결제 정보(TID)가 없습니다.");
            }

            // payload(사유)에는 요약 또는 거절사유를 담아 추적성 향상
            String payload = partial ? rejectReason : "승인수량:" + approvedQtyTotal;

            Map<String, Object> kakaoRes = kakaoApiService.cancel(payment.getTid(), finalRefundAmount, payload);
            pgPayloadJson = toJson(kakaoRes); // 원문 저장
            pgRefundId = extractKakaoCancelId(kakaoRes); // 있으면 저장(없으면 null 허용)
        }

        // 6) 상위 엔티티 저장 (문자열 status 직접 대입)
        LocalDateTime now = LocalDateTime.now();
        refund.setTotalRefundAmount(finalRefundAmount);
        refund.setPgRefundId(pgRefundId);
        refund.setPgPayloadJson(pgPayloadJson);
        refund.setRejectedReason(partial ? rejectReason : null);
        refund.setStatus("REFUNDED"); // ← 문자열 직입력
        refund.setUpdateDate(now);
        refundRepository.save(refund);

        order.setStatus("REFUNDED"); // ← 문자열 직입력
        orderRepository.save(order);

        // 7) 문자 발송(승인/거절 "건수" + 필요 시 거절사유 포함)
        String to = (refund.getUser() != null ? refund.getUser().getPhone() : null);
        String userName = (refund.getUser() != null ? safe(refund.getUser().getName()) : "");
        Long orderIdVal = getOrderId(order);

        if (to != null && !to.isBlank()) { 
            // 거절된 상품이 0 이라면, -- 전체 승인
            if (rejectedLines == 0) {
                refundSmsService.sendFullApproval(
                        to, userName, orderIdVal,
                        finalRefundAmount, approvedQtyTotal, now);
            } else {        // 부분 환불(거절 포함)
                refundSmsService.sendPartialApproval(
                        to, userName, orderIdVal,
                        finalRefundAmount, approvedQtyTotal, rejectedQtyTotal,
                        now, rejectReason);
            }
        }

        // 8) 결과
        return ApproveRefundResult.success(
                refund.getRefundId(),
                refund.getStatus(), // "REFUNDED"
                finalRefundAmount,
                partial,
                approvedQtyTotal,
                shippingRefund,
                rejectedQtyTotal,
                itemSubtotal
        );
    }

    // ===== 유틸 =====
    private static int nvl(Integer v, int def) {
        return v == null ? def : v;
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(v, max));
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private static String nullIfBlank(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private String toJson(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (Exception e) {
            return "{\"marshal\":\"failed\"}";
        }
    }

    private Long getOrderId(Order order) {
        // 프로젝트에 맞게 하나만 남겨도 됨
        try {
            return (Long) Order.class.getMethod("getOrderId").invoke(order);
        } catch (ReflectiveOperationException ignore) {
            try {
                return (Long) Order.class.getMethod("getId").invoke(order);
            } catch (ReflectiveOperationException e) {
                return 0L;
            }
        }
    }

    // Kakao CANCEL 응답에서 취소 식별자 추출(있으면)
    private String extractKakaoCancelId(Map<String, ?> res) {
        if (res == null)
            return null;
        // 오픈 API 응답에 취소 고유키가 없다면 null 가능
        Object v = res.get("aid");
        if (v == null)
            v = res.get("cancel_id");
        if (v == null)
            v = res.get("refund_id");
        return (v == null) ? null : String.valueOf(v);
    }
}