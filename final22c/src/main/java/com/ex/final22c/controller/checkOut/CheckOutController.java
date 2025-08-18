
package com.ex.final22c.controller.checkOut; 
import java.security.Principal;
import java.util.List;

import org.springframework.stereotype.Controller; 
import org.springframework.ui.Model; 
import org.springframework.web.bind.annotation.GetMapping; 
import org.springframework.web.bind.annotation.PathVariable; 
import org.springframework.web.bind.annotation.PostMapping; 
import org.springframework.web.bind.annotation.RequestMapping; 
import org.springframework.web.bind.annotation.RequestParam;
 
import com.ex.final22c.data.product.Product;
import com.ex.final22c.data.user.UserAddress;
import com.ex.final22c.data.user.Users;
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
  
     

     return "pay/checkout";
 }
}
