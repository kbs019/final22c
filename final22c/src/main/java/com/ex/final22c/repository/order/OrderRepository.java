package com.ex.final22c.repository.order;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;


import com.ex.final22c.data.order.Order;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    /** 마이페이지 목록: 사용자 + 상태(예: PAID) 페이징 조회
     *  EntityGraph로 details/product를 미리 로딩해서 Lazy 예외 방지
     */


	// PENDING 제외하고 전체 리스트
	@EntityGraph(attributePaths = {"details", "details.product"})
	List<Order> findAllByUser_UserNoAndStatusNotOrderByRegDateDesc(
	        Long userNo, String status
	);

	// 특정 상태들만(IN 조건)
	@EntityGraph(attributePaths = {"details", "details.product"})
	List<Order> findByUser_UserNoAndStatusInOrderByRegDateDesc(
	        Long userNo, Collection<String> statuses);

    // 특정 상태를 제외하고 싶을때
    @EntityGraph(attributePaths = {"details", "details.product"})
    Page<Order> findByUser_UserNoAndStatusNotOrderByRegDateDesc(
            Long userNo, String status, Pageable pageable);

    @EntityGraph(attributePaths = {"details", "details.product"})
    Page<Order> findByUser_UserNoOrderByRegDateDesc(Long userNo, Pageable pageable);

    /** 단건 조회: 주문 + 상세 + 상품까지 fetch-join (결제승인/취소 등 트랜잭션 로직에서 사용) */
    @Query("""
        select o
          from Order o
          left join fetch o.details d
          left join fetch d.product p
         where o.orderId = :orderId
    """)
    Optional<Order> findOneWithDetails(@Param("orderId") Long orderId);
    
    
    /** 스케줄러 배송중, 배송완료 변경*/
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
                         @Param("threshold3") LocalDateTime threshold3
    );   
            
    @Modifying
    @Query("""
        UPDATE Order o
          SET o.deliveryStatus = 'CONFIRMED'
        WHERE o.orderId = :orderId
          AND o.user.userNo = :userNo
          AND o.status = 'PAID'
          AND o.deliveryStatus = 'DELIVERED'
    """)
    int updateToConfirmed(@Param("orderId") Long orderId, Long userNo);
}
