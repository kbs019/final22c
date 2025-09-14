package com.ex.final22c.data.user;

import java.sql.Timestamp;

public interface MileageUsageProjection {
    Timestamp getRegDate();          // date → regDate
    int getUsedPoint();
    int getConfirmedMileage();
    int getRefundMileage();
    int getRefundRebate();
    Long getDescription();
}
