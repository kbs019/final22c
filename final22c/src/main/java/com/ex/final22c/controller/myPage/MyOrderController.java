package com.ex.final22c.controller.myPage;

import java.security.Principal;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.ex.final22c.data.order.Order;
import com.ex.final22c.service.order.MyOrderService;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
@RequestMapping("/mypage")
public class MyOrderController {
	private final MyOrderService myOrderService;
	
	@GetMapping("/order")
	public String list(@RequestParam(name = "page", defaultValue = "0") int page,
					   @RequestParam(name = "size", defaultValue = "10") int size,
					   Principal principal,
					   Model model) {
		if(principal == null) {
			return "redirect:/user/login";
		}
		Page<Order> orders = myOrderService.listMyOrders(principal.getName(), page, size);
		model.addAttribute("orders",orders);
		return "mypage/orders";
	}

	@GetMapping("/order/fragment")
	public String listFragment(@RequestParam(defaultValue = "0") int page,
							   @RequestParam(defaultValue = "10") int size,
							   Principal principal, Model model) {
		// 로그인 안되어 있을 시 로그인 화면으로
		if( principal == null ) return "redirect:/user/login";

		Page<Order> orders = myOrderService.listMyOrders(principal.getName(), page, size);
		model.addAttribute("orders", orders);
		return "mypage/orders :: listAndPager";
	}
	
}
