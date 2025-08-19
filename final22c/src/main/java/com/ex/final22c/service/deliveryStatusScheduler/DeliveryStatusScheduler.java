package com.ex.final22c.service.deliveryStatusScheduler;

import java.time.LocalDateTime;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

import com.ex.final22c.repository.order.OrderRepository;

import lombok.RequiredArgsConstructor;


@RequiredArgsConstructor
public class DeliveryStatusScheduler {
	 private final OrderRepository orderRepository;

	    @Scheduled(cron = "0 */10 * * * *")  // 10분마다
	    @Transactional
	    public void advanceDeliveryStatus() {
	        LocalDateTime now = LocalDateTime.now();
	        LocalDateTime t1 = now.minusDays(1);
	        LocalDateTime t3 = now.minusDays(3);

	        int delivered = orderRepository.updateToDelivered(t3);
	        int shipping = orderRepository.updateToShipping(t1, t3);

	        System.out.printf("Delivery status updated: delivered=%d, shipping=%d%n",
	                          delivered, shipping);
	    }
}
