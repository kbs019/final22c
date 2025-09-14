package com.ex.final22c.controller.myPage;

import java.security.Principal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.ex.final22c.data.order.Order;
import com.ex.final22c.data.order.OrderDetail;
import com.ex.final22c.data.payment.Payment;
import com.ex.final22c.repository.order.OrderRepository;
import com.ex.final22c.service.order.MyOrderService;

import com.ex.final22c.service.refund.RefundService;

import com.ex.final22c.service.product.ReviewService;


import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
@RequestMapping("/mypage")
public class MyOrderController {

    private final OrderRepository orderRepository;
    private final RefundService refundService;
	private final MyOrderService myOrderService;
	private final ReviewService reviewService;


    /** 전체 페이지(레이아웃 포함) */
    @GetMapping("/order")
    public String listPage(@RequestParam(name = "page", defaultValue = "0") int page,
                           @RequestParam(name = "size", defaultValue = "10") int size,
                           @RequestParam(name = "statuses", required = false) List<String> statuses,
                           Principal principal,
                           Model model) {
        if (principal == null) {
            return "redirect:/user/login";
        }

        Page<Order> orders = findOrders(principal.getName(), page, size, statuses);
        model.addAttribute("orders", orders);

        // 환불완료 주문들의 부분환불 여부 맵
        Map<Long, Boolean> partialRefundMap = orders.getContent().stream()
                .filter(o -> "REFUNDED".equalsIgnoreCase(o.getStatus()))
                .collect(Collectors.toMap(
                        Order::getOrderId,
                        o -> refundService.hasPartialRefund(o.getOrderId())
                ));
        model.addAttribute("partialRefundMap", partialRefundMap);

        model.addAttribute("section", "orders");
        model.addAttribute("statusFilter",
                (statuses == null || statuses.isEmpty()) ? "EXCEPT_PENDING" : String.join(",", statuses));

        return "mypage/orders";
    }

    /** 목록+페이저 프래그먼트 (AJAX용) */
    @GetMapping("/order/fragment")
    public String listFragment(@RequestParam(name = "page", defaultValue = "0") int page,
                               @RequestParam(name = "size", defaultValue = "10") int size,
                               @RequestParam(name = "statuses", required = false) List<String> statuses,
                               Principal principal,
                               Model model) {
        if (principal == null) {
            return "redirect:/user/login";
        }

        Page<Order> orders = findOrders(principal.getName(), page, size, statuses);
        model.addAttribute("orders", orders);
        Map<Long, Boolean> partialRefundMap = orders.getContent().stream()
                .filter(o -> "REFUNDED".equalsIgnoreCase(o.getStatus()))
                .collect(Collectors.toMap(
                        Order::getOrderId,
                        o -> refundService.hasPartialRefund(o.getOrderId())
                ));
        model.addAttribute("partialRefundMap", partialRefundMap);

        model.addAttribute("section", "orders");
        model.addAttribute("statusFilter",
                (statuses == null || statuses.isEmpty()) ? "EXCEPT_PENDING" : String.join(",", statuses));

        // ordersSection 프래그먼트만 반환
        return "mypage/orders :: ordersSection";
    }

    /** 공통 조회 로직 */
    private Page<Order> findOrders(String username, int page, int size, List<String> statuses) {
        if (statuses == null || statuses.isEmpty()) {
            // 기본: PENDING 제외(서비스에서 처리한다고 가정)
            return myOrderService.listMyOrders(username, page, size);
        } else {
            // 필요 시 상태 필터 서비스로 분기
            return myOrderService.listMyOrders(username, page, size);
        }
    }

    /** 주문 상세 프래그먼트 (모달에서 불러감) */
    @GetMapping("/order/{id}/fragment")
    public String orderItemsFragment(@PathVariable("id") Long id,
                                     Principal principal, Model model) {
        Order order = myOrderService.findMyOrderWithDetails(principal.getName(), id);
        List<Payment> payments = myOrderService.findPaymentsofOrder(id);
        model.addAttribute("order", order);
        model.addAttribute("payments", payments);
        // ✔ orderDetail.html 안의 th:fragment="items" 반환
        return "mypage/orderDetail :: items";
    }

    @GetMapping("/orders/{orderId}/mileage")
    @ResponseBody
    public int getConfirmMileage(@PathVariable("orderId") Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("주문을 찾을 수 없습니다."));
        return order.getConfirmMileage();
    }

    @GetMapping("/order/{orderId}/refund-results-v2")
    @ResponseBody
    public com.ex.final22c.data.refund.dto.RefundResultDtoV2 getRefundResultV2(
            @PathVariable(name = "orderId") Long orderId,
            Principal principal) {

        if (principal != null) {
            myOrderService.findMyOrderWithDetails(principal.getName(), orderId);
        }
        return refundService.getLatestRefundResultV2(orderId);
    }

    @GetMapping("/order/{orderId}/refund-results-v2/list")
    @ResponseBody
    public java.util.List<com.ex.final22c.data.refund.dto.RefundResultDtoV2> getRefundResultsV2(
            @PathVariable(name = "orderId") Long orderId,
            Principal principal) {

        if (principal != null) {
            myOrderService.findMyOrderWithDetails(principal.getName(), orderId);
        }
        return refundService.getRefundResultsV2(orderId);
    }
    
	/** 주문 상세 전체 페이지 */
	@GetMapping("/order/{id}")
	public String orderDetailPage(@PathVariable("id") Long id,
	                              Principal principal,
	                              Model model) {
	    if (principal == null) {
	        return "redirect:/user/login";
	    }

	    String username = principal.getName();

	    // 주문 + 상세 정보 조회
	    Order order = myOrderService.findMyOrderWithDetails(username, id);
	    List<Payment> payments = myOrderService.findPaymentsofOrder(id);

	    // ✅ 각 상품별 리뷰 작성 여부 세팅 (String 'Y'/'N')
	    for (OrderDetail d : order.getDetails()) {
	        boolean exists = reviewService.existsReview(username, d.getProduct().getId());
	        d.setReviewWritten(exists ? "Y" : "N"); // Boolean → 'Y'/'N'
	    }

	    model.addAttribute("order", order);
	    model.addAttribute("payments", payments);
	    model.addAttribute("section", "orders");

	    return "mypage/orderDetail";
	}
}
