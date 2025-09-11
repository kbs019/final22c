package com.ex.final22c.data.user;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class MileageRowWithBalance {
    private Long orderId;
    private LocalDateTime processedAt;

    /** 결제 차감(양수), 환불 복구는 음수로 세팅(표시는 +복구) */
    private int usedPointRaw;

    /** 적립(양수), 회수는 음수 */
    private int earnPointVisible;

    /** 해당 행 처리 이후 잔액 스냅샷 */
    private int balanceAt;

    /** 화면 표시용 상태: PAID / CONFIRMED / REFUNDED */
    private String status;
}
