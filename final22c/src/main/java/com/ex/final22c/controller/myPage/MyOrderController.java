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

	@GetMapping("/order")
	public String listFragment(@RequestParam(name= "page", defaultValue = "0") int page,
							   @RequestParam(name= "size",defaultValue = "10") int size,
							   Principal principal, Model model) {
		// 로그인 안되어 있을 시 로그인 화면으로
		if(principal == null) {
			return "redirect:/user/login";
		}

		Page<Order> orders = myOrderService.listMyOrders(principal.getName(), page, size);
		model.addAttribute("orders", orders);
		model.addAttribute("section", "orders");
		return "mypage/orders";
	}

	public String list(@RequestParam(name = "page", defaultValue = "0") int page,
					   @RequestParam(name = "size", defaultValue = "10") int size,
					   @RequestParam(name = "statuses", required = false) List<String> statuses,
					   Principal principal,
					   Model model) {
			
		if (principal == null) return "redirect:/user/login";
		Page<Order> orders;
			
		if (statuses == null || statuses.isEmpty()) {
		 // 기본: PENDING 제외 (서비스에 이미 구현해 둔 메서드)
		orders = myOrderService.listMyOrders(principal.getName(), page, size);
		model.addAttribute("statusFilter", "EXCEPT_PENDING");
		} else {
		orders = myOrderService.listMyOrders(principal.getName(), page, size);
		model.addAttribute("statusFilter", String.join(",", statuses));
		}
			
		model.addAttribute("orders", orders);
		model.addAttribute("page", page);
		model.addAttribute("size", size);
		return "mypage/orders";
	}

	@GetMapping("/order/{id}/fragment")
	public String orderItemsFragment(@PathVariable("id") Long id,
									Principal principal, Model model) {

		Order order = myOrderService.findMyOrderWithDetails(principal.getName(), id);
		List<Payment> payments = myOrderService.findPaymentsofOrder(id);
		model.addAttribute("order", order);
		model.addAttribute("payments", payments);
		return "mypage/orderDetail :: items";
	}

	
}
