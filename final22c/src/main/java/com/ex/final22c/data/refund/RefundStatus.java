package com.ex.final22c.data.refund;

public enum RefundStatus {
    PENDING,     // 사용자 요청(승인 대기)
    APPROVED,    // 관리자 승인(환불 처리 완료)
    REJECTED,    // 관리자 거절
    CANCELED     // 사용자가 요청 취소(선택)
}
