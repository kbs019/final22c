package com.ex.final22c.repository.refund;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.ex.final22c.data.order.Order;
import com.ex.final22c.data.refund.Refund;

@Repository
public interface RefundRepository extends JpaRepository<Refund, Long> {
    List<Refund> findAllByOrderByCreateDateDesc();

    /** 해당 주문에 REQUESTED 상태 환불이 존재하는지 */
    boolean existsByOrderAndStatus(Order order, String status);

    /** 관리자 상세 모달에 필요한 연관관계 즉시 로딩 */
    @Query("""
        select distinct r
        from Refund r
            join fetch r.order o
            join fetch r.user u
            join fetch r.payment p
            left join fetch r.details d
            left join fetch d.orderDetail od
            left join fetch od.product pr
        where r.refundId = :refundId
    """)
    Optional<Refund> findGraphById(@Param("refundId") Long refundId);
}