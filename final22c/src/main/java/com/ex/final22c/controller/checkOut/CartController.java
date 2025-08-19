package com.ex.final22c.controller.checkOut;

import java.security.Principal;
import java.util.HashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.ex.final22c.data.cart.IdsPayload;
import com.ex.final22c.data.user.Users;
import com.ex.final22c.service.cart.CartDetailService;
import com.ex.final22c.service.cart.CartService;
import com.ex.final22c.service.user.UsersService;

import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;
    private final CartDetailService cartDetailService;
    private final UsersService usersService;

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

    @PatchMapping("/check/{id}/qty")
    public ResponseEntity<Map<String, Object>> changeQty(
            @PathVariable("id") Long id,
            @RequestParam(name = "qty") int qty,
            Principal principal) {

        String userName = principal.getName();
        int clamped = cartDetailService.clampAndUpdateQty(userName, id, qty);

        // 최신 재고를 응답에 포함하려면 재조회 (findMine은 fetch join)
        int stock = cartDetailService.findMine(userName, id).getProduct().getCount();

        Map<String, Object> body = new HashMap<>();
        body.put("qty", clamped);
        body.put("max", stock);
        body.put("ok", clamped == qty);

        return (clamped == qty)
                ? ResponseEntity.ok(body)
                : ResponseEntity.status(HttpStatus.CONFLICT).body(body);
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
        Users user = usersService.getUser(userName);
        model.addAttribute("userMileage", user.getMileage());
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
        int n = cartDetailService.removeMine(principal.getName(), payload.getIds());
        return ResponseEntity.noContent()
                .header("X-Deleted-Count", String.valueOf(n))
                .build();
    }
}
