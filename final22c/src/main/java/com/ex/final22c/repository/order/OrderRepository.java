package com.ex.final22c.repository.order;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.ex.final22c.data.order.Order;
import com.ex.final22c.data.user.Row;

import jakarta.persistence.LockModeType;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

  // 관리자 대시보드용
  @Query("""
        select count(o)
          from Order o
        where o.regDate >= :from and o.regDate <= :to
          and o.status in :statuses
      """)
  long countByRegDateBetweenAndStatusIn(@Param("from") LocalDateTime from,
      @Param("to") LocalDateTime to,
      @Param("statuses") Collection<String> statuses);

  /**
   * 마이페이지 목록: 사용자 + 상태(예: PAID) 페이징 조회
   * EntityGraph로 details/product를 미리 로딩해서 Lazy 예외 방지
   */

  // PENDING 제외하고 전체 리스트
  @EntityGraph(attributePaths = { "details", "details.product" })
  List<Order> findAllByUser_UserNoAndStatusNotOrderByRegDateDesc(
      Long userNo, String status);

  // 특정 상태들만(IN 조건)
  @EntityGraph(attributePaths = { "details", "details.product" })
  List<Order> findByUser_UserNoAndStatusInOrderByRegDateDesc(
      Long userNo, Collection<String> statuses);

  // 특정 상태를 제외하고 싶을때
  @EntityGraph(attributePaths = { "details", "details.product" })
  Page<Order> findByUser_UserNoAndStatusNotOrderByRegDateDesc(
      Long userNo, String status, Pageable pageable);

  @EntityGraph(attributePaths = { "details", "details.product" })
  Page<Order> findByUser_UserNoOrderByRegDateDesc(Long userNo, Pageable pageable);

  @EntityGraph(attributePaths = { "details", "details.product" })
  Page<Order> findByUser_UserNoAndStatusNotInOrderByRegDateDesc(
      Long userNo, Collection<String> statuses, Pageable pageable);

  @EntityGraph(attributePaths = { "details", "details.product" })
  Page<Order> findByUser_UserNoAndStatusInOrderByRegDateDesc(
      Long userNo,
      Collection<String> statuses,
      Pageable pageable);

  /** 단건 조회: 주문 + 상세 + 상품까지 fetch-join (결제승인/취소 등 트랜잭션 로직에서 사용) */
  @Query("""
          select o
            from Order o
            left join fetch o.details d
            left join fetch d.product p
           where o.orderId = :orderId
      """)
  Optional<Order> findOneWithDetails(@Param("orderId") Long orderId);

  /** 스케줄러 배송중, 배송완료 변경 */
  @Modifying
  @Query("""
          UPDATE Order o
             SET o.deliveryStatus = 'DELIVERED'
           WHERE o.status = 'PAID'
             AND o.deliveryStatus IN ('ORDERED','SHIPPING')
             AND o.regDate <= :threshold3
      """)
  int updateToDelivered(@Param("threshold3") LocalDateTime threshold3);

  @Modifying
  @Query("""
          UPDATE Order o
             SET o.deliveryStatus = 'SHIPPING'
           WHERE o.status = 'PAID'
             AND o.deliveryStatus = 'ORDERED'
             AND o.regDate <= :threshold1
             AND o.regDate > :threshold3
      """)
  int updateToShipping(@Param("threshold1") LocalDateTime threshold1,
      @Param("threshold3") LocalDateTime threshold3);

  // 주문확정 클릭시 order status를 confirmed로 변경
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("""
         UPDATE Order o
            set o.status = 'CONFIRMED'
          where o.orderId = :orderId
            and o.user.userNo = :userNo
            and o.status = 'PAID'
            and o.deliveryStatus = 'DELIVERED'
      """)
  int updateToConfirmed(@Param("orderId") Long orderId,
      @Param("userNo") Long userNo);

  /* ===== 사용자별 단건 조회 (details + product fetch) ===== */
  @Query("""
          select o
          from Order o
          left join fetch o.details d
          left join fetch d.product p
          where o.orderId = :orderId
            and o.user.userName = :username
      """)
  Optional<Order> findOneWithDetailsAndProductByUser(@Param("username") String username,
      @Param("orderId") Long orderId);

  /* == 결제 대기중인 상태가 지속됐을때 failed로 변경 == */
  @Modifying
  @Query("""
        update Order o
           set o.status = 'FAILED'
         where o.status = 'PENDING'
           and o.regDate <= :threshold
      """)
  int failOldPendings(@Param("threshold") LocalDateTime threshold);

  // OrderRepository
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select o from Order o where o.orderId = :id")
  Optional<Order> findByIdForUpdate(@Param("id") Long id);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("update Order o set o.status = :status where o.orderId = :id")
  int updateStatus(@Param("id") Long id, @Param("status") String status);

  Optional<Order> findByOrderIdAndUser_UserName(long orderId, String userName);

  List<Order> findByUser_UserNameOrderByRegDateDesc(String userName);

  interface MileageRowBare {
    Long getOrderId();

    java.time.LocalDateTime getProcessedAt(); // 처리시각

    Integer getUsedPointRaw(); // 결제차감(양수)

    Integer getEarnPointVisible(); // 적립/환불반환(양수)

    String getStatus(); // PAID / CONFIRMED / REFUNDED
  }

  // 차감(결제 시 사용 포인트)
  @Query("""
      select new com.ex.final22c.data.user.Row(
        o.orderId, o.regDate, o.usedPoint, 0, 'PAID'
      )
        from Order o
        where o.user.userNo = :userNo
        and coalesce(o.usedPoint,0) > 0
        and o.status in ('PAID','CONFIRMED','REFUNDED','CANCELED')
      """)
  List<com.ex.final22c.data.user.Row> findPaid(@Param("userNo") Long userNo);

  // 적립(구매확정 시 적립 포인트)
  @Query("""
        select new com.ex.final22c.data.user.Row(
          o.orderId,
          o.regDate,
          0,
          coalesce(o.confirmMileage, 0),
          'CONFIRMED'
        )
        from Order o
        where o.user.userNo = :userNo
          and o.status = 'CONFIRMED'
      """)
  List<com.ex.final22c.data.user.Row> findConfirmed(@Param("userNo") Long userNo);

  // 환불(환불 시 복구된 마일리지)
  @Query("""
          select new com.ex.final22c.data.user.Row(
            r.order.orderId,
            max(r.updateDate),
            0,
            coalesce(sum(r.refundMileage), 0),
            'REFUNDED'
          )
          from Refund r
          where r.user.userNo = :userNo and r.status = 'REFUNDED'
          group by r.order.orderId
      """)
  List<com.ex.final22c.data.user.Row> findRefunded(@Param("userNo") Long userNo);

  // 환불(현금 환불 합계)
  @Query("""
        select new com.ex.final22c.data.user.Row(
          r.order.orderId,
          max(r.updateDate),
          0,
          coalesce(sum(r.totalRefundAmount), 0),
          'REFUNDED_CASH'
        )
        from Refund r
        where r.user.userNo = :userNo and r.status = 'REFUNDED'
        group by r.order.orderId
      """)
  List<com.ex.final22c.data.user.Row> findRefundedCash(@Param("userNo") Long userNo);

  @Query("""
          select new com.ex.final22c.data.user.Row(
            o.orderId,
            o.regDate,
            0,
            coalesce(o.totalAmount, 0),
            'ORDER_CASH'
          )
          from Order o
          where o.user.userNo = :userNo
      """)
  List<com.ex.final22c.data.user.Row> findOrderCash(@Param("userNo") Long userNo);

  // 각 주문의 '상품합계'(Σ detail.totalPrice) 스냅샷
  @Query("""
        select new com.ex.final22c.data.user.Row(
          o.orderId,
          o.regDate,
          0,
          coalesce(sum(d.totalPrice), 0),
          'ITEMS_SUBTOTAL'
        )
        from Order o
        join o.details d
        where o.user.userNo = :userNo
        group by o.orderId, o.regDate
      """)
  List<com.ex.final22c.data.user.Row> findItemsSubtotal(@Param("userNo") Long userNo);

  @Query("""
        select new com.ex.final22c.data.user.Row(
          o.orderId,
          o.regDate,
          0,
          coalesce(o.confirmMileage, 0),
          'CONFIRM_SNAPSHOT'
        )
        from Order o
        where o.user.userNo = :userNo and o.status = 'CONFIRMED'
      """)
  List<com.ex.final22c.data.user.Row> findConfirmSnapshot(@Param("userNo") Long userNo);

  public interface MileageRow {
    Long getOrderId();

    java.time.LocalDateTime getProcessedAt();

    Long getUsedPoint();

    // 계산용: 확정 +, 환불 − (기존)
    Long getEarnedPoint();

    // 표시용: 확정/환불 모두 양수(원래 적립될 금액)
    Long getVisibleEarnPoint();

    String getStatus();
  }

  @Query("""
      select
          o.orderId                            as orderId,
          o.regDate                            as processedAt,
          coalesce(o.usedPoint, 0)             as usedPoint,
          case
              when o.status = 'REFUNDED' then 0
              else cast(function('trunc', coalesce(o.totalAmount, 0) * 0.05) as long)
          end                                   as earnedPoint,
          o.status                             as status
      from Order o
      where o.user.userNo = :userNo
        and o.status in :statuses
      order by o.regDate desc
      """)
  Page<MileageRow> findMileageByUserAndStatuses(
      @Param("userNo") Long userNo,
      @Param("statuses") Collection<String> statuses,
      Pageable pageable);
}
