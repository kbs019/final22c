package com.ex.final22c.repository.mypage;

import java.time.LocalDateTime;
import java.util.List;

import com.ex.final22c.data.order.Order;
import com.ex.final22c.data.refund.Refund;

/** 마일리지 화면 전용 읽기 리포지토리 (Spring 빈은 구현 클래스로 등록) */
public interface MileageReadRepository {

    /** 결제 시 사용한 마일리지 합계 (payment_detail.used_mileage SUM) */
    long sumUsedMileageOfOrder(Long orderId);

    /** 결제 승인 시각(없으면 null) — Payment.type/status 참조 금지 */
    LocalDateTime findPaidAt(Long orderId);

    /** 환불(완료) 목록 */
    List<Refund> findRefundedByUser(Long userNo);

    /** 마일리지 집계 대상 주문 */
    List<Order> findOrdersForMileage(Long userNo);
}
