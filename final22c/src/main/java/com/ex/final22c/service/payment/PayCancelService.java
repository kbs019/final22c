package com.ex.final22c.service.payment;

import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ex.final22c.data.order.Order;
import com.ex.final22c.service.KakaoApiService;
import com.ex.final22c.service.order.OrderService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PayCancelService {
	private final PaymentService paymentService;
	private final KakaoApiService kakaoApiService;
	private final OrderService orderService;
	
	@Transactional
	public Map<String, Object> cancelPaid(Long orderId, String reason){
		// 주문/결제 조회 및  상태 검증
		Order order = orderService.get(orderId);
		var pay = paymentService.getLatestByOrderId(orderId);
	
		// 이미 취소된 건 멱등 처리(그냥 반환하거나 예외)
	    if ("CANCELED".equalsIgnoreCase(order.getStatus()) || "CANCELED".equalsIgnoreCase(pay.getStatus())) {
	        return Map.of("alreadyCanceled", true);
	    }
	    
		if (!"PAID".equalsIgnoreCase(order.getStatus()) || !"SUCCESS".equalsIgnoreCase(pay.getStatus())) {
			throw new IllegalStateException("취소 불가 상태");
		}
		
		int amountToCancel = order.getTotalAmount();
			
		
		// 취소(카카오)
		Map<String, Object> pgResp = kakaoApiService.cancel(pay.getTid(),amountToCancel, reason);
		
		// 마일리지 복구 / 재고 복구 / 상태
		orderService.applyCancel(order, amountToCancel, reason);
		
		return pgResp;
	}
	
}
