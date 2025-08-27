package com.ex.final22c.data.refund;

import java.util.List;

import org.springframework.messaging.handler.annotation.Header;

import com.ex.final22c.data.product.Product;

import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class RefundDetailResponse {
    private Header header;
    private List<Item> items;

        @Builder @Getter
    public static class Header {
        private String userName;
        private Long   orderId;
        private String createdAt;
        private String reason;
        private String status;
        private String paymentTid; // UI 미표시 가능(추후 PG 환불 시 사용)
        private int usedPoint;      // 주문 사용 마일리지(승인 모달 summary에서 보여줌)
    }

    @Builder @Getter
    public static class Item {
        private Long refundDetailId;
        private Integer unitRefundAmount;  // 단가
        private Integer quantity;          // 요청수량
        private Integer refundQty;         // 승인수량(없으면 프론트에서 quantity로 초기화)
        private ProductLite product;
    }

    @Builder @Getter
    public static class ProductLite {
        private Long id;
        private String name;
        private String imgPath;
        private String imgName;
    }
}
