package com.ex.final22c.service.order;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.ex.final22c.data.order.Order;
import com.ex.final22c.data.payment.Payment;
import com.ex.final22c.data.refund.Refund;
import com.ex.final22c.data.refund.RefundDetail;
import com.ex.final22c.data.user.Users;
import com.ex.final22c.repository.order.OrderRepository;
import com.ex.final22c.repository.orderDetail.OrderDetailRepository;
import com.ex.final22c.repository.payment.PaymentRepository;
import com.ex.final22c.repository.refund.RefundRepository;
import com.ex.final22c.repository.user.UserRepository;
import com.ex.final22c.service.user.UsersService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class MyOrderService {
    private final UsersService usersService;
    private final OrderRepository orderRepository;
    private final UserRepository usersRepository;
    private final PaymentRepository paymentRepository;
    private final OrderDetailRepository orderDetailRepository;
    private final RefundRepository refundRepository;

    /**
     * 마이페이지 목록(페이징): PENDING, failed 제외하고(PAID + CANCELED + REFUND) 최신순
     */

    @Transactional(readOnly = true)
    public Page<Order> listMyOrders(String username, int page, int size) {
        Users me = usersService.getUser(username);
        Pageable pageable = PageRequest.of(page, size, Sort.by("regDate").descending());

        // 확정건 포함
        List<String> visible = List.of("PAID", "CONFIRMED", "REFUNDED", "CANCELED", "REQUESTED");
        return orderRepository.findByUser_UserNoAndStatusInOrderByRegDateDesc(
                me.getUserNo(), visible, pageable);
    }

    /**
     * 세부 포함 전체 리스트(페이징 없음): PENDING 제외하고(PAID + CANCELED) 최신순
     */
    @Transactional(readOnly = true)
    public List<Order> listMyOrdersWithDetails(String username) {
        Users me = usersService.getUser(username);
        return orderRepository.findAllByUser_UserNoAndStatusNotOrderByRegDateDesc(
                me.getUserNo(), "PENDING");

    }

    /**
     * (옵션) 상태 필터를 명시하고 싶을 때: IN 조건 사용
     * 예) ["PAID","CANCELED"] 만 보고 싶을 때
     */
    @Transactional(readOnly = true)
    public List<Order> listMyOrdersByStatuses(String username, Collection<String> statuses) {
        Users me = usersService.getUser(username);
        return orderRepository.findByUser_UserNoAndStatusInOrderByRegDateDesc(
                me.getUserNo(), statuses);

    }

    // 주문 확정
    @Transactional
    public void confirmOrder(String username, Long orderId) {
        Users me = usersService.getUser(username);

        int updated = orderRepository.updateToConfirmed(orderId, me.getUserNo());
        if (updated == 0) {
            throw new IllegalStateException("확정 불가 상태이거나 주문이 없습니다.");
        }
    }

    // 주문 확정시 마일리지 지급 + 확정된 건에 대하여 확정수량 넣기
    @Transactional
    public Order confirmOrderAndAwardMileage(String username, Long orderId) {
        Users me = usersService.getUser(username);

        // 1) 상태 전환
        int updated = orderRepository.updateToConfirmed(orderId, me.getUserNo());
        if (updated == 0)
            throw new IllegalStateException("확정 불가 상태이거나 주문을 찾을 수 없습니다.");

        // 2) 확정된 주문 재조회(상세는 프런트 표시용)
        Order order = orderRepository.findOneWithDetails(orderId)
                .orElseThrow(() -> new IllegalStateException("주문을 찾을 수 없습니다."));

        // 3) 적립 마일리지: Σ detail.totalPrice(배송비 제외) × 5% 내림
        int itemsTotal = (order.getDetails() == null) ? 0
                : order.getDetails().stream()
                        .mapToInt(d -> java.util.Objects.requireNonNullElse(d.getTotalPrice(), 0))
                        .sum();

        int mileage = (int) Math.floor(Math.max(0, itemsTotal) * 0.05);

        // 4) 스냅샷 저장(내역 화면이 이 값을 우선 사용)
        order.setConfirmMileage(mileage);
        orderRepository.save(order);

        // 5) 사용자 잔액 증가
        if (mileage > 0) {
            usersRepository.addMileage(me.getUserNo(), mileage);
        }

        return order;
    }

    @Transactional(readOnly = true)
    public Order findMyOrderWithDetails(String username, Long orderId) {
        return orderRepository.findOneWithDetailsAndProductByUser(username, orderId)
                .orElseThrow(() -> new IllegalArgumentException("주문을 찾을 수 없습니다."));
    }

    @Transactional(readOnly = true)
    public List<Payment> findPaymentsofOrder(Long orderId) {
        return paymentRepository.findByOrder_OrderId(orderId);
    }

    /** 주문별 환불요청(REQUESTED) 목록을 Map 구조로 반환 */

    /** 주문별 환불요청 목록(JSON용 Map) */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getRefundRequestsOfOrder(long orderId, String principalName) {

        // 소유자 검증(없으면 404) – Users.userName 기준
        orderRepository.findByOrderIdAndUser_UserName(orderId, principalName)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        // 1) 우선 REQUESTED만
        List<Refund> refunds = refundRepository
                .findByOrder_OrderIdAndStatusOrderByCreateDateDesc(orderId, "REQUESTED");

        // 2) 비어 있으면 (상태가 다를 가능성) → 전부 가져와서 상태 로그
        if (refunds.isEmpty()) {
            List<Refund> all = refundRepository.findByOrder_OrderIdOrderByCreateDateDesc(orderId);
            System.out.println("[refund-requests] orderId=" + orderId
                    + ", REQUESTED=0, allCount=" + all.size()
                    + ", statuses=" + all.stream().map(Refund::getStatus).toList());

            // 화면엔 최소한 요청/완료/거절은 보여주자(원하면 목록에만 쓰고, 상단 배지는 REQUESTED일 때만 노출)
            refunds = refundRepository.findByOrder_OrderIdAndStatusInOrderByCreateDateDesc(
                    orderId, List.of("REQUESTED", "REFUNDED", "REJECTED", "CANCELED"));

        }

        // 3) JSON 변환: ‘요청 수량(quantity)’ 기준으로 금액 계산
        List<Map<String, Object>> result = new ArrayList<>(refunds.size());
        for (Refund r : refunds) {
            Map<String, Object> item = new HashMap<>();
            item.put("refundId", r.getRefundId());
            item.put("status", nvl(r.getStatus(), "REQUESTED"));
            item.put("reason", nvl(r.getRequestedReason(), ""));
            item.put("createdAt", r.getCreateDate());

            List<Map<String, Object>> details = new ArrayList<>();
            if (r.getDetails() != null) {
                for (RefundDetail d : r.getDetails()) {
                    Map<String, Object> det = new HashMap<>();
                    String productName = (d.getOrderDetail() != null && d.getOrderDetail().getProduct() != null)
                            ? d.getOrderDetail().getProduct().getName()
                            : "-";
                    int qty = safeInt(d.getQuantity()); // 신청 수량
                    int unit = safeInt(d.getUnitRefundAmount()); // 단가 스냅샷

                    det.put("productName", productName);
                    det.put("refundQty", qty);
                    det.put("unitRefundAmount", unit);
                    det.put("subtotal", qty * unit);
                    details.add(det);
                }
            }
            item.put("details", details);
            result.add(item);
        }
        return result;

    }

    private static String nvl(String s, String def) {
        return s == null ? def : s;
    }

    private static int safeInt(Integer n) {
        return n == null ? 0 : n;
    }
}