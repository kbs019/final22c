package com.ex.final22c.data.user;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class MileageUsageDto {
    private java.sql.Timestamp date;          // date_col → date
    private int usedPoint;                     // used_point → usedPoint
    private int confirmedMileage;              // confirmed_mileage → confirmedMileage
    private int refundMileage;                 // refund_mileage → refundMileage
    private int refundRebate;                  // refund_rebate → refundRebate
    private Long description;

    public java.time.LocalDateTime getDate() {
        return date.toLocalDateTime();
    }
}
