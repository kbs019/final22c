package com.ex.final22c.data.refund;

import java.util.List;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class RefundResultResponse {
    private Header header;
    private Summary summary;
    private List<Item> items;
    private Flags flags;

    @Getter @Setter @Builder
    public static class Header {
        private String userName;
        private Long   orderId;
        private String createdAt;
        private String userReason;
        private String processedAt;
        private String approver;         // 선택
        private String rejectionReason;  // 공통 거절 사유(부분 환불 시)
    }

    @Getter @Setter @Builder
    public static class Summary {
        private Integer requestedQty;
        private Integer approvedQty;
        private Integer itemsSubtotal;
        private Integer shippingRefund;
        private Integer deduction;
        private Integer totalRefundAmount;
    }

    @Getter @Setter @Builder
    public static class Item {
        private ProductLite product;
        private Integer unitRefundAmount;
        private Integer requestedQty;
        private Integer approvedQty;
        private Integer lineAmount;
    }

    @Getter @Setter @Builder
    public static class ProductLite {
        private String name;
        private String imgPath;
        private String imgName;
    }

    @Getter @Setter @Builder
    public static class Flags {
        private boolean partialRejected;
    }
}
