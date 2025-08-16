package com.ex.final22c.service.order;

import com.ex.final22c.data.order.Order;
import com.ex.final22c.data.product.Product;
import com.ex.final22c.data.user.Users;
import com.ex.final22c.repository.order.OrderRepository;
import com.ex.final22c.repository.user.UserRepository;
import com.ex.final22c.service.product.ProductService;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductService productService;
    private final UserRepository usersRepository;

    private static final int SHIPPING_FEE = 3000;

    /** 결제 대기 주문 생성 (포인트/배송비 반영) */
    @Transactional
    public Order createPendingOrder(String userId, long productId, int qty, int usedPoint) {
        Users user = usersRepository.findByUserName(userId)
                .orElseThrow(() -> new IllegalStateException("로그인이 필요합니다. userId=" + userId));

        Product p = productService.getProduct(productId);
        if (p == null) throw new IllegalArgumentException("상품 없음: " + productId);

        int quantity  = Math.max(1, qty);
        int unitPrice = (int)Math.round(p.getPrice() * 0.7);
        int lineTotal = unitPrice * quantity;

        int owned = (user.getMileage() == null) ? 0 : user.getMileage();
        int use   = Math.max(0, Math.min(usedPoint, Math.min(owned, lineTotal + SHIPPING_FEE)));
        int payable = Math.max(0, lineTotal + SHIPPING_FEE - use);

        Order order = new Order();
        order.setUser(user);
        order.setUsedPoint(use);
        order.setTotalAmount(payable);     // 스냅샷
        // status, regDate 는 @PrePersist 로 PENDING/now 자동 세팅

        return orderRepository.save(order);
    }

    /** 하위호환 */
    @Transactional
    public Order createPendingOrder(String userId, long productId, int qty) {
        return createPendingOrder(userId, productId, qty, 0);
    }

    /** 단건 조회 */
    @Transactional(readOnly = true)
    public Order get(Long orderId) {
        return orderRepository.findById(orderId.intValue())
                .orElseThrow(() -> new IllegalArgumentException("주문 없음: " + orderId));
    }

    /** 결제 성공(승인) 처리 — 재호출되어도 안전하게 */
    @Transactional
    public void markPaid(Long orderId) {
        Order o = get(orderId);
        if ("PAID".equalsIgnoreCase(o.getStatus())) return; // 멱등
        o.setStatus("PAID");
        // TODO: 여기서 재고 차감/판매량 증가/포인트 차감 등을 처리
        // e.g. usersRepository.updateMileage(o.getUser().getUserNo(), -o.getUsedPoint());
        //      productService.decreaseStock(...);
        //      paymentService.linkOrderPaid(...);
    }

    /** 사용자 취소(팝업 닫힘 감지 등) — PENDING일 때만 취소 */
    @Transactional
    public void markCanceled(Long orderId) {
        Order o = get(orderId);
        if ("PAID".equalsIgnoreCase(o.getStatus())) return;  // 이미 결제됨 → 무시
        if (!"CANCELED".equalsIgnoreCase(o.getStatus())) {
            o.setStatus("CANCELED");
            // TODO: 필요하면 재고 복구/로그 남기기 등
        }
    }
}
