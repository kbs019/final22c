package com.ex.final22c.service.payment;

import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ex.final22c.data.order.Order;
import com.ex.final22c.data.payment.Payment;
import com.ex.final22c.repository.order.OrderRepository;
import com.ex.final22c.repository.payment.PaymentRepository;
import com.ex.final22c.service.KakaoApiService;
import com.ex.final22c.service.order.OrderService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PayCancelService {
	private final PaymentService paymentService;
	private final KakaoApiService kakaoApiService;
	private final OrderService orderService;
	private final OrderRepository orderRepository;
	private final PaymentRepository paymentRepository;
	
	@Transactional
	public Map<String, Object> cancelPaid(Long orderId, String reason) {
	    // 0) 주문/결제 조회 (details fetch-join)
	    Order order = orderRepository.findOneWithDetails(orderId)
	        .orElseThrow(() -> new IllegalArgumentException("주문 없음: " + orderId));
	    Payment pay = paymentRepository.findTopByOrder_OrderIdOrderByPaymentIdDesc(orderId)
	        .orElseThrow(() -> new IllegalArgumentException("결제 정보 없음(orderId): " + orderId));

	    // 1) 멱등성(이미 변경된 값에 덮어쓰기 x) / 상태 검증
	    if ("CANCELED".equalsIgnoreCase(order.getStatus())) {
	        // 이미 취소된 주문이면 PG/DB 추가 조작 없이 반환
	        return Map.of("alreadyCanceled", true);
	    }
	    if (!"PAID".equalsIgnoreCase(order.getStatus())) {
	        throw new IllegalStateException("취소 불가 상태(주문이 PAID 아님)");
	    }
	    if (!"SUCCESS".equalsIgnoreCase(pay.getStatus())) {
	        throw new IllegalStateException("취소 불가 상태(결제가 SUCCESS 아님)");
	    }
	    
	    // 2) 0원 결제일 경우 / PG 미사용 
	    if (pay.getAmount() == 0) {
	    	// 포인트 환불 + 주문 상태 CANCELED 처리
	    	orderService.applyCancel(order, 0, reason);
	    	paymentService.markCanceledByTid(reason);
	    	return Map.of("ok", true, "message", "0원 결제건 환불 완료(포인트 복구)");
	    }

	    // 3) PG 취소 (전액취소만 지원)
	    final int amountToCancel = order.getTotalAmount(); // 부분취소 미지원
	    Map<String, Object> pgResp = kakaoApiService.cancel(pay.getTid(), amountToCancel, reason);

	    // 4) DB 취소 처리 (마일리지/재고 복구 + 주문 상태 업데이트)
	    orderService.applyCancel(order, amountToCancel, reason);

	    // 5) 결제 상태도 CANCELED로 표시
	    paymentService.markCanceledByTid(pay.getTid());

	    return pgResp;
	}

}
