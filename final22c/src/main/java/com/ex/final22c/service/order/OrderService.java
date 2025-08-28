package com.ex.final22c.service.order;

import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ex.final22c.data.cart.CartLine;
import com.ex.final22c.data.order.Order;
import com.ex.final22c.data.order.OrderDetail;
import com.ex.final22c.data.payment.dto.ShipSnapshotReq;
import com.ex.final22c.data.product.Product;
import com.ex.final22c.data.user.Users;
import com.ex.final22c.repository.order.OrderRepository;
import com.ex.final22c.repository.orderDetail.OrderDetailRepository;
import com.ex.final22c.repository.user.UserRepository;
import com.ex.final22c.service.cart.CartService;
import com.ex.final22c.service.product.ProductService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductService productService;
    private final UserRepository usersRepository;
    private final OrderDetailRepository orderDetailRepository;
    private final CartService cartService;

    private static final int SHIPPING_FEE = 3000;

    /**
     * 결제 대기 주문 생성 (단건) — 가격 스냅샷 고정 + 재고 ‘예약(감소)’
     */
    @Transactional
    public Order createPendingOrder(String userId, long productId, int qty, int usedPoint, ShipSnapshotReq ship) {
        Users user = usersRepository.findByUserName(userId)
                .orElseThrow(() -> new IllegalStateException("로그인이 필요합니다. userId=" + userId));

        Product p = productService.getProduct(productId);
        if (p == null) throw new IllegalArgumentException("상품 없음: " + productId);

        int quantity  = Math.max(1, qty);
        int unitPrice = p.getSellPrice();     // ← 가격 스냅샷 단가
        int lineTotal = unitPrice * quantity; // ← 가격 스냅샷 합계

        // 마일리지 계산(서버에서 클램프)
        int owned = (user.getMileage() == null) ? 0 : user.getMileage();
        int maxUsable = Math.min(owned, lineTotal + SHIPPING_FEE);
        int use   = Math.max(0, Math.min(usedPoint, maxUsable));
        int payable = lineTotal + SHIPPING_FEE - use;

        // 1) 재고 ‘예약’(감소) — 승인 전이지만 오버셀 방지용으로 미리 잡아둠
        //    실패 시(재고 부족 등) 예외 발생 → 트랜잭션 롤백
        productService.decreaseStock(p.getId(), quantity);

        // 2) 주문 + 디테일(가격 스냅샷) 저장
        Order order = new Order();
        order.setUser(user);
        order.setUsedPoint(use);
        order.setTotalAmount(payable);     // 스냅샷 결제금
        order.setShippingSnapshot(ship);   // 배송지 스냅샷
        // status, regDate 는 @PrePersist 로 PENDING/now 자동 세팅

        OrderDetail d = new OrderDetail();
        d.setProduct(p);
        d.setQuantity(quantity);
        d.setSellPrice(unitPrice);    // ← 스냅샷
        d.setTotalPrice(lineTotal);   // ← 스냅샷
        order.addDetail(d);

        return orderRepository.save(order);
    }

    /** 하위호환 */
    @Transactional
    public Order createPendingOrder(String userId, long productId, int qty, int usedPoint) {
        return createPendingOrder(userId, productId, qty, usedPoint, null);
    }

    /** 단건 조회 */
    @Transactional(readOnly = true)
    public Order get(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("주문 없음: " + orderId));
    }

    /**
     * 결제 성공(승인) — 멱등
     * 재고는 이미 PENDING 시점에 예약(감소)했으므로 여기서 추가 차감은 하지 않습니다.
     * 마일리지만 차감하고 상태만 PAID로 변경합니다.
     */
    @Transactional
    public void markPaid(Long orderId) {
        // 디테일까지 로딩해서 LAZY 예외 방지
        Order o = orderRepository.findOneWithDetails(orderId)
            .orElseThrow(() -> new IllegalArgumentException("주문 없음: " + orderId));

        if ("PAID".equalsIgnoreCase(o.getStatus())) return; // 멱등

        // 0) 결제 성공했으니까 장바구니에서 삭제해주기
        var ids = o.getSelectedCartDetailIds();
        if (ids != null && !ids.isEmpty()) {
        	String username = o.getUser().getUserName();
        	cartService.removeAll(username, ids);
        	o.getSelectedCartDetailIds().clear();  // -> 조인테이블에도 행삭제
        }
        
        // 1) 마일리지 차감
        int used = o.getUsedPoint();
        if (used > 0) {
            int updated = usersRepository.deductMileage(o.getUser().getUserNo(), used);
            if (updated != 1) throw new IllegalStateException("마일리지 차감 실패/부족");
        }

        // 2) 재고 추가 차감 없음 (이미 예약됨)

        // 3) 상태 갱신
        o.setStatus("PAID");
        o.setDeliveryStatus("ORDERED");
        orderRepository.save(o);
        
        // 4) PAID시 quantity만큼 confirmquantity에 추가
        orderDetailRepository.fillConfirmQtyToQuantity(orderId);
    }

    /**
     * 사용자 취소(팝업 닫힘 등) — PENDING에서만 취소 허용
     * 재고는 PENDING에서 이미 예약(감소)했으므로, 여기서 ‘해제(증가)’ 합니다.
     */
    @Transactional
    public void markCanceled(Long orderId) {
        Order o = orderRepository.findOneWithDetails(orderId)
                .orElseThrow(() -> new IllegalArgumentException("주문 없음: " + orderId));

        if ("PAID".equalsIgnoreCase(o.getStatus())) return;  // 이미 결제됨 → 무시
        if ("CANCELED".equalsIgnoreCase(o.getStatus())) return; // 멱등

        if ("PENDING".equalsIgnoreCase(o.getStatus())) {
            // 재고 해제(증가)
            for (OrderDetail d : o.getDetails()) {
                productService.increaseStock(d.getProduct().getId(), d.getQuantity());
            }
        }
        o.setStatus("CANCELED");
        orderRepository.save(o);
    }

    /**
     * 결제 후 취소(환불) 적용
     * - 마일리지 복구
     * - (전액 환불 시) 재고 복구
     * - 상태: PAID -> CANCELED
     */
    @Transactional
    public void applyCancel(Order order, int cancelAmount, String reason) {
        // 마일리지 복구
        int used = order.getUsedPoint();
        if (used > 0) {
            int restore = (cancelAmount >= order.getTotalAmount()) ? used : Math.min(used, cancelAmount);
            if (restore > 0) usersRepository.addMileage(order.getUser().getUserNo(), restore);
        }

        // 재고 복구 (전액 환불 시)
        if (cancelAmount >= order.getTotalAmount()) {
            for (OrderDetail d : order.getDetails()) {
                productService.increaseStock(d.getProduct().getId(), d.getQuantity());
            }
        }

        // 상태 전이
        order.setStatus("CANCELED");
        orderRepository.saveAndFlush(order);
    }

    /**
     * 결제 실패/중단(PENDING에서 머무르다 실패)
     * - PENDING → FAILED
     * - 재고는 PENDING에서 예약(감소)되어 있으므로 여기서 해제(증가)
     */
    @Transactional
    public void markFailedPending(Long orderId) {
        Order o = orderRepository.findOneWithDetails(orderId)
                .orElseThrow(() -> new IllegalArgumentException("주문 없음: " + orderId));

        if ("PAID".equalsIgnoreCase(o.getStatus())) return; // 이미 결제됨 → 무시
        if (!"PENDING".equalsIgnoreCase(o.getStatus())) {
            // 이미 CANCELED/FAILED라면 멱등 처리
            return;
        }

        // 재고 해제(증가)
        for (OrderDetail d : o.getDetails()) {
            productService.increaseStock(d.getProduct().getId(), d.getQuantity());
        }

        // 상태 전이
        o.setStatus("FAILED"); // 결제창 단계 실패/중단
        orderRepository.save(o);
    }

    /**
     * 장바구니(다건) 결제 대기 주문 생성
     * - 각 라인 가격 스냅샷 고정
     * - 각 라인 재고 ‘예약(감소)’, 하나라도 실패하면 전체 롤백
     */
    @Transactional
    public Order createCartPendingOrder(String userId,
                                        List<CartLine> lines,
                                        int itemsTotal,    // 서버 재계산 합계(참고)
                                        int shipping,      // 보통 3000
                                        int usedPoint,     // 서버 클램프
                                        int payableHint,   // 프론트 계산 값(참고용)
                                        ShipSnapshotReq ship,
                                        List<Long> selectedCartDetailIds) {
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("선택된 상품이 없습니다.");
        }

        Users user = usersRepository.findByUserName(userId)
                .orElseThrow(() -> new IllegalStateException("로그인이 필요합니다. userId=" + userId));

        // 서버 기준 마일리지/결제금액 재계산
        int owned = Objects.requireNonNullElse(user.getMileage(), 0);
        int shipfee  = Math.max(0, shipping);
        int maxUsable = Math.min(owned, itemsTotal + shipfee);
        int use = Math.max(0, Math.min(usedPoint, maxUsable));
        int payable = Math.max(0, itemsTotal + shipfee - use);

        // 1) 먼저 모든 라인의 재고 ‘예약’(감소)
        //    하나라도 실패하면 예외 발생 → 트랜잭션 롤백
        for (CartLine l : lines) {
            int qty = Math.max(1, l.getQuantity());
            Product p = productService.getProduct(l.getId());
            if (p == null) throw new IllegalArgumentException("상품 없음: " + l.getId());
            productService.decreaseStock(p.getId(), qty); // 예약
        }

        // 2) 주문 + 각 라인의 가격 스냅샷 저장
        Order order = new Order();
        order.setUser(user);
        order.setUsedPoint(use);
        order.setTotalAmount(payable);   // 스냅샷 결제금
        order.setShippingSnapshot(ship); // 배송지 스냅샷
        
        order.selectedCartDetailIds(selectedCartDetailIds);
        
        for (CartLine l : lines) {
            Product p = productService.getProduct(l.getId()); // 영속 엔티티
            int qty  = Math.max(1, l.getQuantity());
            int unit = p.getSellPrice();                      // ← 서버 기준 단가 스냅샷
            OrderDetail d = new OrderDetail();
            d.setProduct(p);
            d.setQuantity(qty);
            d.setSellPrice(unit);             // 스냅샷
            d.setTotalPrice(unit * qty);      // 스냅샷
            order.addDetail(d);
        }

        return orderRepository.save(order);
    }
}
