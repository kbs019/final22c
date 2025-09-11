package com.ex.final22c.repository.mypage;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Repository;

import com.ex.final22c.data.order.Order;
import com.ex.final22c.data.refund.Refund;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@Repository
public class MileageReadRepositoryJpa implements MileageReadRepository {

    @PersistenceContext
    private EntityManager em;

    @Override
    public long sumUsedMileageOfOrder(Long orderId) {
        // Long.class 대신 Number.class 로 받아서 안전 캐스팅
        Number n = em.createQuery("""
                    select coalesce(o.usedPoint, 0)
                      from Order o
                     where o.orderId = :orderId
                """, Number.class)
                .setParameter("orderId", orderId)
                .getSingleResult();
        return n != null ? n.longValue() : 0L;
    }

    @Override
    public LocalDateTime findPaidAt(Long orderId) {
        // 이건 그대로 OK (Order.regDate 가 LocalDateTime)
        return em.createQuery("""
                    select o.regDate
                      from Order o
                     where o.orderId = :orderId
                """, LocalDateTime.class)
                .setParameter("orderId", orderId)
                .getSingleResult();
    }

    @Override
    public List<Refund> findRefundedByUser(Long userNo) {
        return em.createQuery("""
                    select r
                      from Refund r
                     where r.user.userNo = :userNo
                       and r.status = 'REFUNDED'
                     order by coalesce(r.updateDate, r.createDate) asc
                """, Refund.class)
                .setParameter("userNo", userNo)
                .getResultList();
    }

    @Override
    public List<Order> findOrdersForMileage(Long userNo) {
        return em.createQuery("""
                    select o
                      from Order o
                     where o.user.userNo = :userNo
                       and o.status in ('PAID','CONFIRMED','REQUESTED','REFUNDED')
                """, Order.class)
                .setParameter("userNo", userNo)
                .getResultList();
    }
}
