package com.ex.final22c.service.order;

import com.ex.final22c.data.order.Order;
import com.ex.final22c.data.order.OrderDetail;
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


    /** 결제 대기 주문 생성 (포인트/배송비 반영) */
    @Transactional
    public Order createPendingOrder(String userId, long productId, int qty, int usedPoint) {
        Users user = usersRepository.findByUserName(userId)
                .orElseThrow(() -> new IllegalStateException("로그인이 필요합니다. userId=" + userId));

        Product p = productService.getProduct(productId);
        if (p == null) throw new IllegalArgumentException("상품 없음: " + productId);

        int quantity  = Math.max(1, qty);
        int unitPrice = p.getSellPrice();
        int lineTotal = unitPrice * quantity;

        int owned = (user.getMileage() == null) ? 0 : user.getMileage();
        int shipping = 3000;
        int maxUsable = Math.min(owned, lineTotal + shipping); // 마일리지는 결제금액을 넘길 수 없음
        int use   = Math.max(0, Math.min(usedPoint, maxUsable));
        int payable = lineTotal + shipping - use;

        Order order = new Order();
        order.setUser(user);
        order.setUsedPoint(use);
        order.setTotalAmount(payable);     // 스냅샷
        // status, regDate 는 @PrePersist 로 PENDING/now 자동 세팅

        OrderDetail d = new OrderDetail();
        d.setProduct(p);
        d.setQuantity(quantity);
        d.setSellPrice(unitPrice);
        d.setTotalPrice(lineTotal);
        order.addDetail(d);
        
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
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("주문 없음: " + orderId));
    }

    /** 결제 성공(승인) 처리 — 재호출되어도 안전하게 */
    @Transactional
    public void markPaid(Long orderId) {
        // 디테일까지 로딩해서 LAZY 예외 방지
        Order o = orderRepository.findOneWithDetails(orderId)
            .orElseThrow(() -> new IllegalArgumentException("주문 없음: " + orderId));

        if ("PAID".equalsIgnoreCase(o.getStatus())) return; // 멱등

        // 1) 마일리지 차감
        int used = o.getUsedPoint();
        if (used > 0) {
            int updated = usersRepository.deductMileage(o.getUser().getUserNo(), used);
            if (updated != 1) throw new IllegalStateException("마일리지 차감 실패/부족");
        }

        // 2) 재고 차감
        for (OrderDetail d : o.getDetails()) {
            productService.decreaseStock(d.getProduct().getId(), d.getQuantity());
        }

        // 3) 상태 갱신
        o.setStatus("PAID");
        orderRepository.save(o);
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
    // 결제 취소
    @Transactional
    public void applyCancel(Order order, int cancelAmount, String reason) {
    	// 마일리지 복구
    	int used = order.getUsedPoint();
    	if(used > 0) {
    		int restore = (cancelAmount >= order.getTotalAmount()) ? used : Math.min(used , cancelAmount);
    		if(restore > 0 ) usersRepository.addMileage(order.getUser().getUserNo(), restore);
    	}
    	
    	// 재고 복구
    	if(cancelAmount >= order.getTotalAmount()) {
    		for(OrderDetail d : order.getDetails()) {
    			productService.increaseStock(d.getProduct().getId(), d.getQuantity());
    		}
    	}
    	
    	// order 상태 (Paid -> canceled)
    	order.setStatus("CANCELED");
    	orderRepository.saveAndFlush(order);
    }
}
