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

    private static final int MAX_ITEMS = 20; // 라인 수 기준 최대치

    // ------- 헬퍼 -------
    private String requireLogin(Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().isBlank())
            throw new RuntimeException("로그인이 필요합니다.");
        // 보수적으로 익명 토큰 방지
        if ("anonymousUser".equalsIgnoreCase(principal.getName()))
            throw new RuntimeException("로그인이 필요합니다.");
        return principal.getName();
    }

    /** 현재 장바구니 '라인 수' 카운트 */
    @GetMapping("/count")
    @ResponseBody
    public Map<String, Object> count(Principal principal) {
        Map<String, Object> body = new HashMap<>();
        if (principal == null || "anonymousUser".equalsIgnoreCase(principal.getName())) {
            // 비로그인 시 0으로 반환(프런트에서 로그인 유도 가능)
            body.put("count", 0);
            return body;
        }
        int count = cartService.countItems(principal.getName()); // 라인 수 기준
        body.put("count", count);
        return body;
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

    // @PostMapping("/add")
    // public ResponseEntity<?> add(
    //         Principal principal,
    //         @RequestBody AddCartRequest req) {

    //     if (principal == null) {
    //         return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
    //                 .body(Map.of("message", "로그인이 필요합니다."));
    //     }
    //     cartService.addItem(principal.getName(), req.productId(), req.qty());
    //     int count = cartService.countItems(principal.getName());
    //     return ResponseEntity.ok(new AddCartResponse(count));
    // }

    /** 장바구니 담기 (DTO/Record 대신 Map으로 받음) */
    @PostMapping("/add")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> add(
            Principal principal,
            @RequestBody Map<String, Object> req) {

        Map<String, Object> body = new HashMap<>();
        if (principal == null || "anonymousUser".equalsIgnoreCase(principal.getName())) {
            body.put("message", "로그인이 필요합니다.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
        }

        String userName = principal.getName();
        Long productId = (req.get("productId") instanceof Number)
                ? ((Number) req.get("productId")).longValue()
                : null;
        int qty = (req.get("qty") instanceof Number)
                ? ((Number) req.get("qty")).intValue()
                : 1;

        if (productId == null || qty < 1) {
            body.put("message", "요청 값이 올바르지 않습니다.");
            return ResponseEntity.badRequest().body(body);
        }

        // 현재 라인 수
        int current = cartService.countItems(userName);

        // 이미 해당 상품 라인이 있으면 라인 수는 그대로, 없으면 +1이 될 것이므로 미리 판단
        boolean existsLine = cartService.existsLine(userName, productId);
        int willBe = existsLine ? current : current + 1;

        if (willBe > MAX_ITEMS) {
            body.put("code", "CART_LIMIT");
            body.put("message", "장바구니에 담을 수 있는 최대 수량은 20개 입니다.");
            body.put("count", current);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
        }

        // 정상 추가
        cartService.addItem(userName, productId, qty);
        int after = cartService.countItems(userName);

        body.put("count", after);
        body.put("message", "담겼습니다.");
        return ResponseEntity.ok(body);
    }


    // 목록 페이지
    @GetMapping("/list")
    public String list(Model model, Principal principal) {
        String userName = requireLogin(principal);
        Users user = usersService.getUser(userName);

        var products = cartService.findMyCart(userName);
        model.addAttribute("userMileage", user.getMileage());
        model.addAttribute("products", products);

        model.addAttribute("cartCount", products == null ? 0 : products.size());
        model.addAttribute("cartLimit", 20);
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
