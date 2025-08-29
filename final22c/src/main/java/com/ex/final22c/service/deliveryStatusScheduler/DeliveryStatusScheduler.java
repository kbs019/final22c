package com.ex.final22c.service.deliveryStatusScheduler;

import java.time.LocalDateTime;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.ex.final22c.repository.order.OrderRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component                      
@RequiredArgsConstructor
public class DeliveryStatusScheduler {

    private final OrderRepository orderRepository;

    /** 배송상태 자동 전환: ORDERED → SHIPPING → DELIVERED */
    @Scheduled(cron = "0 */10 * * * *") // 10분마다
    @Transactional
    public void advanceDeliveryStatus() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime t1 = now.minusDays(1);
        LocalDateTime t3 = now.minusDays(3);

        int delivered = orderRepository.updateToDelivered(t3);
        int shipping  = orderRepository.updateToShipping(t1, t3);

        log.info("Delivery status updated: delivered={}, shipping={}", delivered, shipping);
    }

    /** 결제대기 만료: 오래된 PENDING → FAILED */
    @Scheduled(cron = "0 */1 * * * *")  // 1분마다 점검
    @Transactional
    public void expireOldPendings() {
        // 정책: 2분 넘게 결제창 단계면 실패 처리
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(2);
        int affected = orderRepository.failOldPendings(threshold);
        if (affected > 0) {
            log.info("Expired old pendings to FAILED: {}", affected);
        }
    }
}
