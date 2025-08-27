package com.ex.final22c.repository.refundDetail;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.ex.final22c.data.order.OrderDetail;
import com.ex.final22c.data.refund.RefundDetail;

@Repository
public interface RefundDetailRepository extends JpaRepository<RefundDetail, Long> {
    RefundDetail findByRefund_RefundId(Long refundId);
    // OrderDetail 엔티티 자체로 조회
    RefundDetail findByOrderDetail(OrderDetail orderDetail);
}
