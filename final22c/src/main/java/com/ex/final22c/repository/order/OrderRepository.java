package com.ex.final22c.repository.order;

import com.ex.final22c.data.order.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    /** 마이페이지 목록: 사용자 + 상태(예: PAID) 페이징 조회
     *  EntityGraph로 details/product를 미리 로딩해서 Lazy 예외 방지
     */
    @EntityGraph(attributePaths = {"details", "details.product"})
    Page<Order> findByUser_UserNoAndStatusOrderByRegDateDesc(
            Long userNo, String status, Pageable pageable
    );

    /** (옵션) 전체 리스트로 받고 싶을 때 — 페이징 없이 */
    @EntityGraph(attributePaths = {"details", "details.product"})
    List<Order> findAllByUser_UserNoAndStatusOrderByRegDateDesc(
            Long userNo, String status
    );

    /** 단건 조회: 주문 + 상세 + 상품까지 fetch-join (결제승인/취소 등 트랜잭션 로직에서 사용) */
    @Query("""
        select o
          from Order o
          left join fetch o.details d
          left join fetch d.product p
         where o.orderId = :orderId
    """)
    Optional<Order> findOneWithDetails(@Param("orderId") Long orderId);
}
