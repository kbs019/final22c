package com.ex.final22c.controller.refund;

import java.security.Principal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;

import com.ex.final22c.data.payment.Payment;
import com.ex.final22c.data.refund.ApproveRefundRequest;
import com.ex.final22c.data.refund.ApproveRefundResult;
import com.ex.final22c.data.refund.Refund;
import com.ex.final22c.data.refund.RefundDetail;
import com.ex.final22c.data.refund.RefundDetailResponse;
import com.ex.final22c.data.refund.RefundResultResponse;
import com.ex.final22c.repository.order.OrderRepository;
import com.ex.final22c.service.refund.RefundService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
@RequestMapping("/refund")
public class RefundController {

    private final RefundService refundService;
    private final OrderRepository orderRepository;

    // 환불내역 상세페이지
    @GetMapping("/{refundId}")
    // @PreAuthorize("hasRole('ADMIN')") // 보안 적용 시 주석 해제
    public ResponseEntity<RefundDetailResponse> getRefundDetail(@PathVariable("refundId") Long refundId) {

        // 서비스에서 Refund + (Order, User, Payment, RefundDetail(Product)) 까지 fetch-join으로
        // 가져오도록 구현
        Refund refund = refundService.getRefundGraphForAdmin(refundId);

        // Header 구성
        RefundDetailResponse.Header header = RefundDetailResponse.Header.builder()
                .userName(refund.getUser().getUserName())
                .orderId(refund.getOrder().getOrderId())
                .createdAt(fmt(refund.getCreateDate()))
                .reason(refund.getRequestedReason())
                .status(refund.getStatus())
                .paymentTid(getTidSafe(refund)) // UI에는 안 쓰더라도 전달해둠
                .usedPoint(nz(refund.getOrder().getUsedPoint())) // 주문 사용 마일리지
                .build();

        // Items 구성
        List<RefundDetailResponse.Item> items = refund.getDetails().stream()
                .sorted(Comparator.comparing(RefundDetail::getRefundDetailId))
                .map(d -> toItem(d))
                .collect(Collectors.toList());

        RefundDetailResponse body = RefundDetailResponse.builder()
                .header(header)
                .items(items)
                .build();

        return ResponseEntity.ok(body);
    }

    // 여기에 둔다(핸들러들 아래, DTO들 위)
    private RefundDetailResponse.Item toItem(RefundDetail d) {
        var od = d.getOrderDetail();
        var p = od.getProduct();

        return RefundDetailResponse.Item.builder()
                .refundDetailId(d.getRefundDetailId())
                .unitRefundAmount(d.getUnitRefundAmount())
                .quantity(d.getQuantity())
                .refundQty(d.getRefundQty())
                .product(RefundDetailResponse.ProductLite.builder()
                        .id(p.getId())
                        .name(p.getName())
                        .imgPath(p.getImgPath())
                        .imgName(p.getImgName())
                        .build())
                .build();
    }

    private String fmt(LocalDateTime t) {
        return (t == null) ? null : t.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
    }

    private String getTidSafe(Refund refund) {
        Payment p = refund.getPayment();
        return (p == null) ? null : p.getTid(); // 네 Payment 엔티티에 tid 게터가 있다고 가정
    }

    // 승인 처리 (승인 버튼 POST)
    /**
     * 1) PathVariable을 사용하는 승인 엔드포인트
     * - URL: POST /refund/{refundId}/approve
     * - Body: ApproveRefundRequest (refundId는 바디에 없어도 됨)
     */
    @PostMapping("/{refundId}/approve")
    public ResponseEntity<?> approveByPath(
            @PathVariable("refundId") Long refundId,
            @Valid @RequestBody ApproveRefundRequest body,
            Principal principal) {
        // body.refundId가 비어있으면 PathVariable로 주입, 있으면 일치 검증
        if (body.getRefundId() == null) {
            body.setRefundId(refundId);
        } else if (!refundId.equals(body.getRefundId())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(error("요청 본문의 refundId와 경로 변수가 일치하지 않습니다."));
        }

        String approver = (principal != null ? principal.getName() : "system");
        ApproveRefundResult result = refundService.approveRefund(body, approver);
        return toResponse(result);
    }

    /**
     * 2) Body만 사용하는 승인 엔드포인트
     * - URL: POST /refund/approve
     * - Body: ApproveRefundRequest (refundId 필수)
     */
    @PostMapping("/approve")
    public ResponseEntity<?> approveByBody(
            @Valid @RequestBody ApproveRefundRequest body,
            Principal principal) {
        if (body.getRefundId() == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(error("refundId는 필수입니다."));
        }

        String approver = (principal != null ? principal.getName() : "system");
        ApproveRefundResult result = refundService.approveRefund(body, approver);
        return toResponse(result);
    }

    // ===== 내부 유틸 =====

    /** 서비스 결과를 적절한 HTTP 상태와 함께 반환 */
    private ResponseEntity<?> toResponse(ApproveRefundResult result) {
        if (result.isOk()) {
            // 정상 처리 → 200 OK
            return ResponseEntity.ok(result);
        }
        // 에러 메시지에 따라 상태코드 매핑(간단 휴리스틱)
        String msg = result.getMessage() != null ? result.getMessage() : "요청을 처리할 수 없습니다.";
        HttpStatus status;
        if (msg.contains("존재하지 않습니다")) {
            status = HttpStatus.NOT_FOUND; // 404
        } else if (msg.contains("이미 처리된")) {
            status = HttpStatus.CONFLICT; // 409
        } else {
            status = HttpStatus.BAD_REQUEST; // 400
        }
        return ResponseEntity.status(status).body(error(msg));
    }

    private Map<String, Object> error(String message) {
        Map<String, Object> m = new HashMap<>();
        m.put("ok", false);
        m.put("message", message);
        return m;
    }

    // ===== 검증/예외 핸들러(컨트롤러 지역) =====

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, Object> m = new HashMap<>();
        m.put("ok", false);
        m.put("message", "유효성 오류");
        Map<String, String> fields = new HashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            fields.put(fe.getField(), fe.getDefaultMessage());
        }
        m.put("fields", fields);
        return ResponseEntity.badRequest().body(m);
    }

    @ExceptionHandler({ IllegalArgumentException.class })
    public ResponseEntity<?> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(error(ex.getMessage()));
    }

    @ExceptionHandler({ IllegalStateException.class })
    public ResponseEntity<?> handleIllegalState(IllegalStateException ex) {
        // 이미 처리된 건 등 상태 충돌성 오류에 주로 매핑
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error(ex.getMessage()));
    }

    /** 환불 처리 결과 조회 (읽기 전용 모달) */
    @GetMapping("/{refundId}/result")
    @ResponseBody
    public ResponseEntity<?> getRefundResult(@PathVariable("refundId") Long refundId) {
        // Refund + (Order, User, Payment, Details…) 를 fetch-join으로 가져오는 기존 서비스 재사용
        Refund r = refundService.getRefundGraphForAdmin(refundId);

        if (r == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("ok", false, "message", "환불건을 찾을 수 없습니다: " + refundId));
        }

        // ---- Header ----
        var header = RefundResultResponse.Header.builder()
                .userName(opt(() -> r.getUser().getUserName()))
                .orderId(opt(() -> r.getOrder().getOrderId()))
                .createdAt(fmt(r.getCreateDate()))
                .userReason(nvl(r.getRequestedReason()))
                .processedAt(fmt(r.getUpdateDate()))
                // approver를 엔티티에 저장하지 않았다면 null 유지 (프런트는 빈 값으로 처리)
                // .approver(nvl(r.getUpdatedBy()))
                .rejectionReason(nvl(r.getRejectedReason())) // 부분 환불 시 공통 거절 사유
                .build();

        // ---- Items ----
        List<RefundDetail> details = (r.getDetails() == null) ? List.of()
                : r.getDetails().stream()
                        .sorted(Comparator.comparing(RefundDetail::getRefundDetailId))
                        .collect(Collectors.toList());

        List<RefundResultResponse.Item> items = details.stream().map(d -> {
            int unit = nz(d.getUnitRefundAmount());
            int requested = nz(d.getQuantity());
            int approved = nz(d.getRefundQty());
            int lineAmt = unit * approved;

            var p = d.getOrderDetail() != null ? d.getOrderDetail().getProduct() : null;

            return RefundResultResponse.Item.builder()
                    .product(RefundResultResponse.ProductLite.builder()
                            .name(p != null ? nvl(p.getName()) : "")
                            .imgPath(p != null ? nvl(p.getImgPath()) : "")
                            .imgName(p != null ? nvl(p.getImgName()) : "")
                            .build())
                    .unitRefundAmount(unit)
                    .requestedQty(requested)
                    .approvedQty(approved)
                    .lineAmount(lineAmt)
                    .build();
        }).collect(Collectors.toList());

        // ---- Summary (배송비/차감 필드가 엔티티에 없다면 0) ----
        int requestedQty = items.stream().mapToInt(i -> nz(i.getRequestedQty())).sum();
        int approvedQty = items.stream().mapToInt(i -> nz(i.getApprovedQty())).sum();
        int itemsSubtotal = items.stream().mapToInt(i -> nz(i.getLineAmount())).sum();
        int shippingRefund = 0; // 필요 시 r.getShippingRefundAmount()로 치환
        int deduction = 0; // 필요 시 r.getDeductionAmount()로 치환
        int refundMileage = nz(r.getRefundMileage()); // refund 엔티티에 저장해둔 값
        int confirmMileage = nz(r.getConfirmMileage()); // refund 엔티티에 저장해둔 값

        Integer persistedTotal = r.getTotalRefundAmount();
        int totalRefundAmount = (persistedTotal != null) ? persistedTotal
                : itemsSubtotal + shippingRefund - deduction;

        var summary = RefundResultResponse.Summary.builder()
                .requestedQty(requestedQty)
                .approvedQty(approvedQty)
                .itemsSubtotal(itemsSubtotal)
                .shippingRefund(shippingRefund)
                .deduction(deduction)
                .totalRefundAmount(totalRefundAmount)
                .refundMileage(refundMileage)
                .confirmMileage(confirmMileage)
                .build();

        // ---- Flags ----
        boolean partialRejected = details.stream()
                .anyMatch(d -> nz(d.getRefundQty()) < nz(d.getQuantity()));

        var flags = RefundResultResponse.Flags.builder()
                .partialRejected(partialRejected)
                .build();

        return ResponseEntity.ok(RefundResultResponse.builder()
                .header(header)
                .summary(summary)
                .items(items)
                .flags(flags)
                .build());
    }

    /* ====== 헬퍼 ====== */
    private static int nz(Integer i) {
        return i == null ? 0 : i;
    }

    private static String nvl(String s) {
        return s == null ? "" : s;
    }

    /** NPE 방지 얇은 람다 */
    private static <T> T opt(Supplier<T> s) {
        try {
            return s.get();
        } catch (Exception e) {
            return null;
        }
    }

    @GetMapping("/pay/order/{orderId}/refund-results")
    public ResponseEntity<?> refundResults(@PathVariable Long orderId, Principal principal) {
        Map<String, Object> body = refundService.getRefundResultsAsMap(orderId);
        return ResponseEntity.ok(body);
    }
}