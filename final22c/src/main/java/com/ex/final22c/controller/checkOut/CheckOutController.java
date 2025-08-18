
package com.ex.final22c.controller.checkOut; 
import java.security.Principal;
import java.util.List;

import org.springframework.stereotype.Controller; 
import org.springframework.ui.Model; 
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable; 
import org.springframework.web.bind.annotation.PostMapping; 
import org.springframework.web.bind.annotation.RequestMapping; 
import org.springframework.web.bind.annotation.RequestParam;

import com.ex.final22c.data.cart.CartSelectionForm;
import com.ex.final22c.data.cart.CartView;
import com.ex.final22c.data.product.Product;
import com.ex.final22c.data.user.UserAddress;
import com.ex.final22c.data.user.Users;
import com.ex.final22c.service.cart.CartService;
import com.ex.final22c.service.mypage.UserAddressService;
import com.ex.final22c.service.product.ProductService; 
import com.ex.final22c.service.user.UsersService;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;


@Controller
@RequiredArgsConstructor
@RequestMapping("/checkout")
public class CheckOutController {

 private final UsersService userService;
 private final ProductService productService;
 private final UserAddressService userAddressService;
 private final CartService cartService;

 private String key(long productId) {
     return "checkout:qty:" + productId;
 }

	 @PostMapping("/order")
	 public String orderOne(@RequestParam("id") long id,
	                        @RequestParam("quantity") int quantity,
	                        HttpSession session) {
	     // 수량 세션에 보관 (삭제하지 않음)
	     session.setAttribute(key(id), quantity);
	     return "redirect:/checkout/order/" + id;
	 }
	
	 @GetMapping("/order/{id}")
	 public String orderOne(@PathVariable("id") long id,
	                        HttpSession session,
	                        Principal principal,
	                        Model model) {
		 if(principal == null) {
			 return "redirect:/user/login";
		 }
		 
	     Integer qty = (Integer) session.getAttribute(key(id));
	     if (qty == null || qty <= 0) qty = 1;   // 기본값
	
	     Product product = productService.getProduct(id);
	
	     int unitPrice = product.getSellPrice();
	     int lineTotal = unitPrice * qty;
	     Users user = userService.getUser(principal.getName());
	     UserAddress defaultAddr = userAddressService.getDefaultAddress(user.getUserNo());
	     
	     model.addAttribute("product", product);
	     model.addAttribute("qty", qty);
	     model.addAttribute("unitPrice", unitPrice);
	     model.addAttribute("lineTotal", lineTotal);
	     model.addAttribute("user", user);
	     model.addAttribute("defaultAddr", defaultAddr);
	     
	     int shipping = 3000;
	     com.ex.final22c.data.cart.CartView summary =
	         com.ex.final22c.data.cart.CartView.builder()
	             .lines(List.of())                 // 빈 라인
	             .subtotal(lineTotal)              // 상품 합계 = 단건 라인 합계
	             .grandTotal(lineTotal + shipping) // 결제금액 = 합계 + 3,000
	             .build();
	     model.addAttribute("summary", summary);
	    return "pay/checkout";
	 }
	 @PostMapping("/order/cart")
	 public String orderFromCart(@ModelAttribute CartSelectionForm form,
	                             Principal principal,
	                             HttpSession session) {
	     if (principal == null) return "redirect:/user/login";

	     var selected = form.toSelectionItems(); // List<CartService.SelectionItem>
	     // 서비스에서 선택 라인 + 합계(배송비 3000 포함)
	     CartView view = cartService.prepareCheckoutView(principal.getName(), selected);

	     session.setAttribute("checkout:cart:view", view);
	     return "redirect:/checkout/order/cart";
	 }

	 @GetMapping("/order/cart")
	 public String orderCart(HttpSession session,
	                         Principal principal,
	                         Model model) {
	     if (principal == null) return "redirect:/user/login";

	     CartView view = (CartView) session.getAttribute("checkout:cart:view");
	     if (view == null || view.getLines().isEmpty()) return "redirect:/cart";

	     var user = userService.getUser(principal.getName());
	     var defaultAddr = userAddressService.getDefaultAddress(user.getUserNo());

	     model.addAttribute("items", view.getLines());     // List<CartLine>
	     model.addAttribute("summary", view);              // subtotal, grandTotal
	     model.addAttribute("shipping", view.getLines().isEmpty() ? 0 : 3000); // 필요 시 별도
	     model.addAttribute("user", user);
	     model.addAttribute("defaultAddr", defaultAddr);

	     return "pay/checkout";
	 }
	 

 
}
