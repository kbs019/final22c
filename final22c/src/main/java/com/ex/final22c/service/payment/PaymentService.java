package com.ex.final22c.service.payment;

import com.ex.final22c.data.order.Order;
import com.ex.final22c.data.order.OrderDetail;
import com.ex.final22c.data.payment.Payment;
import com.ex.final22c.repository.order.OrderRepository;
import com.ex.final22c.repository.payment.PaymentRepository;
import com.ex.final22c.repository.user.UserRepository;
import com.ex.final22c.service.product.ProductService;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;   // 주문 상태 갱신용
    private final UserRepository userRepository;     // 마일리지 차감용
    private final ProductService productService;

    /** Ready */
    public Payment saveReady(Order order, int amount, String tid) {
        Payment p = Payment.builder()
                .order(order)
                .amount(amount)
                .status("READY")
                .tid(tid)
                .build();
        return paymentRepository.save(p);
    }

    /** 승인(Approve) 성공 처리: 마일리지 차감 + 주문 PAID + 결제 SUCCESS (원자적) */
    @Transactional
    public void markSuccess(String tid, String aid, LocalDateTime approvedAt) {
        Payment p = paymentRepository.findByTid(tid)
                .orElseThrow(() -> new IllegalArgumentException("결제 정보 없음(tid): " + tid));

        // 이미 처리된 결제면 재실행 방지(멱등)
        if ("SUCCESS".equals(p.getStatus())) return;

        Order order = p.getOrder();
        if (order == null) throw new IllegalStateException("결제에 연결된 주문 없음");

        // 1) 마일리지 차감 (orders.usedPoint 기준)
        int usedMileage = Optional.ofNullable(order.getUsedPoint()).orElse(0);
        if (usedMileage > 0) {
            Long userNo = order.getUser().getUserNo();
            int updated = userRepository.deductMileage(userNo, usedMileage);
            if (updated != 1) throw new IllegalStateException("마일리지 차감 실패 또는 잔액 부족");
        }
        for (OrderDetail d : order.getDetails()) {
            Long productId = d.getProduct().getId();
            int  qty       = d.getQuantity();
            productService.decreaseStock(productId, qty);
        	}
        // 2) 주문 상태 갱신 (PENDING -> PAID)
        order.setStatus("PAID");
        orderRepository.save(order);

        // 3) 결제 레코드 최종 업데이트
        p.setAid(aid);
        p.setApprovedAt(approvedAt);
        p.setStatus("SUCCESS");
        paymentRepository.save(p);
        // @Transactional 덕분에 모두 함께 커밋/롤백
    }

    public Payment getByTid(String tid) {
        return paymentRepository.findByTid(tid)
                .orElseThrow(() -> new IllegalArgumentException("결제 정보 없음(tid): " + tid));
    }

    public Payment getLatestByOrderId(Long orderId) {
        return paymentRepository.findTopByOrder_OrderIdOrderByPaymentIdDesc(orderId)
                .orElseThrow(() -> new IllegalArgumentException("결제 정보 없음(orderId): " + orderId));
    }
}
