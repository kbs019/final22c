package com.ex.final22c.repository.productRepository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.ex.final22c.data.product.RestockAlert;

@Repository
public interface RestockAlertRepository extends JpaRepository<RestockAlert, Long> {

    boolean existsByProduct_IdAndUser_UserNoAndStatus(@Param("productId") Long productId, @Param("userNo") Long userNo, @Param("status") String status);

    RestockAlert findTopByProduct_IdAndUser_UserNoAndStatus(@Param("productId") Long productId, @Param("userNo") Long userNo, @Param("status") String status);

    List<RestockAlert> findTop500ByProduct_IdAndStatusOrderByRequestedRegAsc(@Param("productId") Long productId, @Param("status") String status);

    // ========================================= 재고 0 -> 양수 입고 시 사용하는 메서드 =============================================================
    // 특정 상품의 입고 알림 대기(R) 목록 + 유저/상품 즉시 로딩
    @EntityGraph(attributePaths = {"user", "product"})
    @Query("""
        select ra
        from RestockAlert ra
        where ra.product.id = :productId
            and ra.status = 'REQUESTED'
    """)
    List<RestockAlert> findRequestedByProductId(@Param("productId") Long productId);

    // 특정 유저가 신청했고, 현재 상품 재고가 양수인 알림들(같이 묶어 보낼 대상)
    @EntityGraph(attributePaths = {"user", "product"})
    @Query("""
        select ra
        from RestockAlert ra
        where ra.user.userNo = :userNo
            and ra.status = 'REQUESTED'
            and ra.product.count > 0
    """)
    List<RestockAlert> findUserRequestedWithStock(@Param("userNo") Long userNo);

    // 발송 성공 일괄 처리 (NOTIFIED + notifiedReg 기록)
    @Modifying
    @Query("""
        update RestockAlert ra
        set ra.status = 'NOTIFIED', ra.notifiedReg = CURRENT_TIMESTAMP
        where ra.restockAlertId in :ids
    """)
    int bulkMarkNotified(@Param("ids") List<Long> ids);

    // 발송 실패 일괄 처리
    @Modifying
    @Query("""
        update RestockAlert ra
        set ra.status = 'FAILED'
        where ra.restockAlertId in :ids
    """)
    int bulkMarkFailed(@Param("ids") List<Long> ids);

    // 쿨다운(최근 N시간 내 동일 유저/상품 발송 여부)
    @Query("""
        select count(ra) > 0
        from RestockAlert ra
        where ra.product.id = :productId
            and ra.user.userNo = :userNo
            and ra.status = 'NOTIFIED'
            and ra.notifiedReg >= :since
    """)
    boolean existsRecentNotified(@Param("productId") Long productId, @Param("userNo") Long userNo, @Param("since") LocalDateTime since);
}
