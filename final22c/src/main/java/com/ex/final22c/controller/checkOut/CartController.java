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
import org.springframework.web.bind.annotation.ResponseBody;

import com.ex.final22c.data.cart.CartView;
import com.ex.final22c.data.cart.IdsPayload;
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

    // ------- 헬퍼 -------
    private String requireLogin(Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().isBlank())
            throw new RuntimeException("로그인이 필요합니다.");
        // 보수적으로 익명 토큰 방지
        if ("anonymousUser".equalsIgnoreCase(principal.getName()))
            throw new RuntimeException("로그인이 필요합니다.");
        return principal.getName();
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

    // 목록 페이지
    @GetMapping("/list")
    public String list(Model model, Principal principal) {
        String userName = requireLogin(principal);
        model.addAttribute("products", cartService.findMyCart(userName));
        return "cart/list";
    }


    /** 선택 삭제 (AJAX) */
    @PostMapping("/remove")
    @ResponseBody
    public ResponseEntity<Void> remove(@RequestBody IdsPayload payload, Principal principal) {
        if (payload == null || payload.getIds() == null || payload.getIds().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        int n = cartService.removeAll(principal.getName(), payload.getIds());
        return ResponseEntity.noContent()
            .header("X-Deleted-Count", String.valueOf(n))
            .build();
    }
}
