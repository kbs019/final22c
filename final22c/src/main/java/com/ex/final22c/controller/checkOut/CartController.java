package com.ex.final22c.controller.checkOut;

import java.security.Principal;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import com.ex.final22c.data.cart.CartView;
import com.ex.final22c.service.cart.CartService;

import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    public record AddCartRequest(Long productId, int qty) {
    }

    public record AddCartResponse(int count) {
    }

    @PostMapping("/add")
    public ResponseEntity<?> add(
            Principal principal,
            @RequestBody AddCartRequest req) {

        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "로그인이 필요합니다."));
        }
        cartService.addItem(principal.getName(), req.productId(), req.qty());
        int count = cartService.countItems(principal.getName());
        return ResponseEntity.ok(new AddCartResponse(count));
    }

    @GetMapping("/list")
    public String list(Principal principal, Model model) {
        if (principal == null)
            return "redirect:/login";

        CartView view = cartService.getCartView(principal.getName()); 
        model.addAttribute("view", view);
        return "/cart/list"; // templates/cart/list.html
    }
}
