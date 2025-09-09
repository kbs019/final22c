package com.ex.final22c.data.user;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class Row {
    private Long orderId;
    private LocalDateTime when;
    private int usedRaw;
    private int earnRaw;
    private String status;

    // ★ HQL sum() → Long 대응용 오버로드 생성자
    public Row(Long orderId, LocalDateTime when, Integer usedRaw, Long earnRaw, String status) {
        this.orderId = orderId;
        this.when = when;
        this.usedRaw = (usedRaw == null ? 0 : usedRaw.intValue());
        this.earnRaw = (earnRaw == null ? 0 : earnRaw.intValue());
        this.status = status;
    }
}