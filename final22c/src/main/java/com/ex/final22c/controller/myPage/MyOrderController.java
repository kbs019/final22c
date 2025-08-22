package com.ex.final22c.controller.myPage;

import java.security.Principal;

import java.util.Map;

import java.util.List;


import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ex.final22c.data.order.Order;
import com.ex.final22c.data.user.Users;
import com.ex.final22c.service.order.MyOrderService;
import com.ex.final22c.service.user.UsersService;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;


@Controller
@RequiredArgsConstructor
@RequestMapping("/mypage")
public class MyOrderController {
	private final MyOrderService myOrderService;
	
	// @GetMapping("/order")
	// public String list(@RequestParam(name = "page", defaultValue = "0") int page,
	// 				   @RequestParam(name = "size", defaultValue = "10") int size,
	// 				   Principal principal,
	// 				   Model model) {
	// 	if(principal == null) {
	// 		return "redirect:/user/login";
	// 	}
	// 	Page<Order> orders = myOrderService.listMyOrders(principal.getName(), page, size);
	// 	model.addAttribute("orders",orders);
	// 	return "mypage/orders";
	// }

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
			 // 선택: IN 필터 (PAID/CANCELED 등 명시)
			 // 필요시 서비스에 Page 버전도 만들어 쓰면 됨. 일단 List → 수동 페이징 예시는 생략.
			 // 여기서는 간단하게 템플릿 기본 흐름을 유지하려면 Page가 필요하니,
			 // Page 버전 메서드가 없다면 기본 경로만 쓰고, 탭/필터는 추후 확장.
			 // → 추천: 서비스에 Page버전 listMyOrdersByStatuses(...) 하나 추가해두기.
			 // 임시로는 기본 동작만 사용:
			 orders = myOrderService.listMyOrders(principal.getName(), page, size);
			 model.addAttribute("statusFilter", String.join(",", statuses));
			}
			
			model.addAttribute("orders", orders);
			model.addAttribute("page", page);
			model.addAttribute("size", size);
			return "mypage/orders";
			}

}
