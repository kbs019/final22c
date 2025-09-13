package com.ex.final22c.repository.refund;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
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
 
    // details → orderDetail → product 까지 한 번에 로딩 (Lazy Exception 방지)
    @EntityGraph(attributePaths = {
        "details", "details.orderDetail", "details.orderDetail.product"
    })
    List<Refund> findByOrder_OrderIdAndStatusOrderByCreateDateDesc(long orderId, String status);

    @EntityGraph(attributePaths = {
    "details","details.orderDetail","details.orderDetail.product"
    })
    List<Refund> findByOrder_OrderIdAndStatusInOrderByCreateDateDesc(long orderId, Collection<String> statuses);

    @EntityGraph(attributePaths = {
    "details","details.orderDetail","details.orderDetail.product"
    })
    List<Refund> findByOrder_OrderIdOrderByCreateDateDesc(long orderId);

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

    @Query("""
        select distinct r.order.orderId
        from Refund r
        where r.status = :status and r.order.orderId in :orderIds
    """)
    List<Long> findRequestedOrderIds(@Param("orderIds") List<Long> orderIds,
                                    @Param("status") String status);
                                    
    Optional<Refund> findGraphByRefundId(@Param("refundId") Long refundId);

    // 환불 승인용: order, order.payment, details, details.orderDetail 로딩
    @EntityGraph(attributePaths = {
        "order",
        "payment",
        "details",
        "details.orderDetail"
    })
    Optional<Refund> findByRefundId(Long refundId);
    
    @Query("""
      select distinct r from Refund r
      left join fetch r.details d
      left join fetch d.orderDetail od
      left join fetch od.product p
      where r.order.orderId = :orderId and r.status = :status
      order by r.updateDate desc
    """)
    List<Refund> findRefundedWithDetails(@Param("orderId") Long orderId,
                                         @Param("status") String status);

    // 주문 ID와 상태로 환불 조회
    List<Refund> findByOrder_OrderIdAndStatus(Long orderId, String status);

    // REQUESTED, REFUNDED 등 상태별 카운트
    long countByStatus(String status);

    Optional<Refund> findTopByOrder_OrderIdOrderByCreateDateDesc(Long orderId);
    
    List<Refund> findByOrder_OrderIdOrderByCreateDateDesc(Long orderId);
}