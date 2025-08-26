package com.ex.final22c.data.refund;

import java.util.List;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ApproveRefundRequest {

    @NotNull
    private Long refundId;
    @NotEmpty
    private List<Item> items;
    private String rejectionReason;

    @Getter @Setter
    public static class Item {
        @NotNull private Long refundDetailId;
        @NotNull @Min(0) private Integer refundQty;
    }
}
