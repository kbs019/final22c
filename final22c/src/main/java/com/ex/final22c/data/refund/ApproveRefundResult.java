package com.ex.final22c.data.refund;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter @Builder
public class ApproveRefundResult {
    private final boolean ok;
    private final String message;

    private final Long refundId;
    private final String status;
    private final int totalRefundAmount;

    private final boolean partial;
    private final int approvedLines;

    private final int shippingRefund;
    private final int rejectedQtyTotal;
    private final int itemSubtotal;

    private ApproveRefundResult(boolean ok, String message, Long refundId,
            String status, int totalRefundAmount,
            boolean partial, int approvedLines, int shippingRefund, int rejectedQtyTotal, int itemSubtotal) {
        this.ok = ok;
        this.message = message;
        this.refundId = refundId;
        this.status = status;
        this.totalRefundAmount = totalRefundAmount;
        this.partial = partial;
        this.approvedLines = approvedLines;
        this.shippingRefund = shippingRefund;
        this.rejectedQtyTotal = rejectedQtyTotal;
        this.itemSubtotal = itemSubtotal;
    }

    public static ApproveRefundResult success(
            Long refundId, String status, int totalRefundAmount,
            boolean partial, int approvedLines,
            int shippingRefund, int rejectedQtyTotal, int itemSubtotal) {

        return ApproveRefundResult.builder()
                .ok(true)
                .message("OK")
                .refundId(refundId)
                .status(status)
                .totalRefundAmount(totalRefundAmount)
                .partial(partial)
                .approvedLines(approvedLines)
                .shippingRefund(shippingRefund)          
                .rejectedQtyTotal(rejectedQtyTotal)     
                .itemSubtotal(itemSubtotal)              
                .build();
    }

    public static ApproveRefundResult error(String msg) {
        return ApproveRefundResult.builder().ok(false).message(msg).build();
    }
}
