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

    @Transactional(readOnly = true)
    public Page<Order> listMyOrders(String username, int page, int size) {
        Users me = usersService.getUser(username);
        Pageable pageable = PageRequest.of(page, size, Sort.by("regDate").descending());
        List<String> visible = List.of("PAID", "CONFIRMED", "REFUNDED", "CANCELED", "REQUESTED");
        return orderRepository.findByUser_UserNoAndStatusInOrderByRegDateDesc(me.getUserNo(), visible, pageable);
    }

    @Transactional(readOnly = true)
    public List<Order> listMyOrdersWithDetails(String username) {
        Users me = usersService.getUser(username);
        return orderRepository.findAllByUser_UserNoAndStatusNotOrderByRegDateDesc(me.getUserNo(), "PENDING");
    }

    @Transactional(readOnly = true)
    public List<Order> listMyOrdersByStatuses(String username, Collection<String> statuses) {
        Users me = usersService.getUser(username);
        return orderRepository.findByUser_UserNoAndStatusInOrderByRegDateDesc(me.getUserNo(), statuses);
    }

    @Transactional
    public void confirmOrder(String username, Long orderId) {
        Users me = usersService.getUser(username);
        int updated = orderRepository.updateToConfirmed(orderId, me.getUserNo());
        if (updated == 0) throw new IllegalStateException("확정 불가 상태이거나 주문이 없습니다.");
    }

    // ★ 배송비 차감 없이 totalAmount 기준 5%
    @Transactional
    public Order confirmOrderAndAwardMileage(String username, Long orderId) {
        Users me = usersService.getUser(username);

        int updated = orderRepository.updateToConfirmed(orderId, me.getUserNo());
        if (updated == 0) throw new IllegalStateException("확정 불가 상태이거나 주문을 찾을 수 없습니다.");

        Order order = orderRepository.findOneWithDetails(orderId)
                .orElseThrow(() -> new IllegalStateException("주문을 찾을 수 없습니다."));

        // 3) 적립 마일리지 계산 및 반영
        int earnBase = Math.max(0, order.getTotalAmount()-3000);
        int mileage = (int) Math.floor(earnBase * 0.05);

        if (mileage > 0) {
            usersRepository.addMileage(me.getUserNo(), mileage);
            order.setConfirmMileage(mileage); // 스냅샷 저장
            orderRepository.save(order);
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

    // --- 환불요청 목록(그대로) ---
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getRefundRequestsOfOrder(long orderId, String principalName) {
        orderRepository.findByOrderIdAndUser_UserName(orderId, principalName)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        List<Refund> refunds = refundRepository
                .findByOrder_OrderIdAndStatusOrderByCreateDateDesc(orderId, "REQUESTED");

        if (refunds.isEmpty()) {
            List<Refund> all = refundRepository.findByOrder_OrderIdOrderByCreateDateDesc(orderId);
            System.out.println("[refund-requests] orderId=" + orderId
                    + ", REQUESTED=0, allCount=" + all.size()
                    + ", statuses=" + all.stream().map(Refund::getStatus).toList());

            refunds = refundRepository.findByOrder_OrderIdAndStatusInOrderByCreateDateDesc(
                    orderId, List.of("REQUESTED", "REFUNDED", "REJECTED", "CANCELED"));
        }

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
                            ? d.getOrderDetail().getProduct().getName() : "-";
                    int qty = safeInt(d.getQuantity());
                    int unit = safeInt(d.getUnitRefundAmount());
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

    private static String nvl(String s, String def) { return s == null ? def : s; }
    private static int safeInt(Integer n) { return n == null ? 0 : n; }
}
