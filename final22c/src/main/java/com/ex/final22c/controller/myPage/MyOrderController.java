package com.ex.final22c.controller.myPage;

import java.security.Principal;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.ex.final22c.data.order.Order;
import com.ex.final22c.data.payment.Payment;
import com.ex.final22c.service.order.MyOrderService;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
@RequestMapping("/mypage")
public class MyOrderController {
	private final MyOrderService myOrderService;

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
			// 로그인 만료 시에도 섹션만 교체되도록 안내 문구를 띄우고 싶다면,
			// orders를 빈 페이지로 내려도 됨(템플릿에서 "주문 내역이 없습니다" 표시)
			return "redirect:/user/login";
		}

		Page<Order> orders = findOrders(principal.getName(), page, size, statuses);
		model.addAttribute("orders", orders);
		model.addAttribute("section", "orders");
		model.addAttribute("statusFilter",
				(statuses == null || statuses.isEmpty()) ? "EXCEPT_PENDING" : String.join(",", statuses));

		// ordersSection 프래그먼트만 반환
		return "mypage/orders :: ordersSection";
	}

	/** 공통 조회 로직 */
	private Page<Order> findOrders(String username, int page, int size, List<String> statuses) {
		if (statuses == null || statuses.isEmpty()) {
			// 기본: PENDING 제외(서비스에 이미 구현되어 있다고 가정)
			return myOrderService.listMyOrders(username, page, size);
		} else {
			// 상태 필터링이 서비스에 따로 있다면 여기서 호출하도록 변경
			return myOrderService.listMyOrders(username, page, size);
		}
	}

	/** 주문 상세 프래그먼트 (모달) */
	@GetMapping("/order/{id}/fragment")
	public String orderItemsFragment(@PathVariable("id") Long id,
			Principal principal, Model model) {
		Order order = myOrderService.findMyOrderWithDetails(principal.getName(), id);
		List<Payment> payments = myOrderService.findPaymentsofOrder(id);
		model.addAttribute("order", order);
		model.addAttribute("payments", payments);
		return "mypage/orderDetail :: items";
	}

	/** 주문 상세 전체 페이지 */
	@GetMapping("/order/{id}")
	public String orderDetailPage(@PathVariable("id") Long id,
			Principal principal,
			Model model) {
		if (principal == null)
			return "redirect:/user/login";

		Order order = myOrderService.findMyOrderWithDetails(principal.getName(), id);
		List<Payment> payments = myOrderService.findPaymentsofOrder(id);

		model.addAttribute("order", order);
		model.addAttribute("payments", payments);
		model.addAttribute("section", "orders");
		return "mypage/orderDetail";
	}
}
