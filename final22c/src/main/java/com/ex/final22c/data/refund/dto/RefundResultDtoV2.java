package com.ex.final22c.data.refund.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL) // null 필드는 JSON에서 생략
public class RefundResultDtoV2 {

    private Long refundId;                 // 환불 ID (헤더에 표시)
    private String status;                 // REFUNDED / REJECTED / PARTIAL ...
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;       // 요청일시 (헤더 오른쪽에 노출)

    private String requestedReason;        // 고객 환불 사유
    private String rejectedReason;         // 거절 사유(엔티티)
    private String adminMetaRejectedReason;// 거절 사유 보조(메타/PG JSON 등)

    private boolean partial;               // 부분환불 여부(계산 값)
    private String badge;                  // 표시 텍스트(예: 전체승인/부분환불/거절)

    private Integer totalRefundAmount;     // 최종 환급 금액 (= 상품합계 + 배송비환급)

    // 환불 상세 품목
    private List<RefundResultItemDto> details;

    @Getter
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class RefundResultItemDto {
        private Long orderDetailId;
        private String productName;
        private Integer requestedQty;      // 요청 수량
        private Integer approvedQty;       // 승인(환불) 수량
        private Integer unitRefundAmount;  // 환불 단가
        private Integer detailRefundAmount;// 품목 환불 합계
    }

    // ===== 요약 카드/하단 합계에서 쓰는 값들 =====
    private Integer refundMileage;         // 환급 마일리지 (카드·하단 둘 다 사용)
    private Integer confirmMileage;        // 적립 마일리지
    private Integer shippingRefundAmount;  // 배송비 환급
    private Integer paymentTotal;          // 결제 총액(payments 없으면 이 값으로 대체)

    private List<PaymentDto> payments;     // 결제 내역(선택)

    @Getter
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class PaymentDto {
        private Long paymentId;
        private String methodName;         // 예: "카카오페이", "카드", "마일리지 전액 결제"
        private String tid;                // PG TID(없으면 마일리지 전액으로 판단)
        private Integer amount;            // 결제 금액
    }
}
