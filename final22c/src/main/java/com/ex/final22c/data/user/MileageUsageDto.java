package com.ex.final22c.data.user;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MileageUsageDto {
    private java.sql.Timestamp date; // 변경
    private String type;
    private int amount;
    private Long description;

    public MileageUsageDto(java.sql.Timestamp date, String type, int amount, Long description) {
        this.date = date;
        this.type = type;
        this.amount = amount;
        this.description = description;
    }

    public java.time.LocalDateTime getDate() {
        return date.toLocalDateTime(); // 필요할 때 LocalDateTime으로 변환
    }

}
