package com.ex.final22c.data.user;

import java.time.LocalDateTime;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MileageRowWithBalance {
    private Long orderId;
    private LocalDateTime processedAt;
    private int usedPointRaw; // 차감(양수)
    private int earnPointVisible; // 적립/환불복구(양수)
    private int balanceAt; // 당시 스냅샷
    private String status; // PAID/CONFIRMED/REFUNDED
}
