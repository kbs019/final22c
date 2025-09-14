package com.ex.final22c.data.user;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.AllArgsConstructor;

import java.sql.Timestamp;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
public class MileageUsageDto {
    private Timestamp regDate;        // date → regDate
    private int usedPoint;
    private int confirmedMileage;
    private int refundMileage;
    private int refundRebate;
    private Long description;

    private int balance; // 누적 계산용

    // 6개 컬럼용 생성자 (Native Query 매핑용)
    public MileageUsageDto(Timestamp regDate, int usedPoint, int confirmedMileage,
                           int refundMileage, int refundRebate, Long description) {
        this.regDate = regDate;              // 파라미터 이름과 일치
        this.usedPoint = usedPoint;
        this.confirmedMileage = confirmedMileage;
        this.refundMileage = refundMileage;
        this.refundRebate = refundRebate;
        this.description = description;
    }

    public java.time.LocalDateTime getDate() {
        return regDate != null ? regDate.toLocalDateTime() : null;
    }

}