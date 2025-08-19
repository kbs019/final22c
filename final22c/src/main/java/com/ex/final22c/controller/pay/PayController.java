package com.ex.final22c.controller.pay;

import java.security.Principal;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;

import com.ex.final22c.data.cart.CartLine;
import com.ex.final22c.data.cart.CartView;
import com.ex.final22c.data.order.Order;
import com.ex.final22c.data.payment.dto.PayCartRequest;
import com.ex.final22c.data.payment.dto.PaySingleRequest;
import com.ex.final22c.service.KakaoApiService;
import com.ex.final22c.service.cart.CartService;
import com.ex.final22c.service.order.OrderService;
import com.ex.final22c.service.payment.PayCancelService;
import com.ex.final22c.service.payment.PaymentService;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
@RequestMapping("/pay")
public class PayController {

    private final OrderService orderService;
    private final KakaoApiService kakaoApiService;
    private final PaymentService paymentService;
    private final PayCancelService payCancelService;
    private final CartService cartService;

    private static final int SHIPPING_FEE = 3000;

    /* ================= 단건 결제 (JSON) ================= */
    @PostMapping(value = "/ready/single", consumes = "application/json", produces = "application/json")
    @ResponseBody
    public Map<String, Object> readySingle(@RequestBody PaySingleRequest req, Principal principal) {
        if (principal == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        final String userId   = principal.getName();
        final long productId  = req.productId();
        final int qty         = Math.max(1, req.quantity() == null ? 1 : req.quantity());
        final int usedPoint   = Math.max(0, req.usedPoint() == null ? 0 : req.usedPoint());

        Order order = orderService.createPendingOrder(userId, productId, qty, usedPoint);
        var ready = kakaoApiService.readySingle(productId, qty, userId, order);
        return Map.of(
                "next_redirect_pc_url", ready.get("next_redirect_pc_url"),
                "orderId", order.getOrderId()
        );
    }

    /* ================= 장바구니 다건 결제 (JSON) ================= */
    @PostMapping(value = "/ready/cart", consumes = "application/json", produces = "application/json")
    @ResponseBody
    public Map<String, Object> readyCart(@RequestBody PayCartRequest req, Principal principal) {
        if (principal == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        final String userId = principal.getName();
        final int usedPoint = Math.max(0, req.usedPoint() == null ? 0 : req.usedPoint());

        var selection = req.items().stream()
                .map(i -> new CartService.SelectionItem(
                        i.cartDetailId(),
                        Math.max(1, i.quantity() == null ? 1 : i.quantity())
                ))
                .toList();

        CartView view = cartService.prepareCheckoutView(userId, selection);
        List<CartLine> lines = view.getLines();
        if (lines.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "선택된 상품이 없습니다.");

        int itemsTotal = view.getSubtotal();
        int shipping   = SHIPPING_FEE;
        int payable    = Math.max(0, itemsTotal + shipping - usedPoint);

        Order order = orderService.createCartPendingOrder(userId, lines, itemsTotal, shipping, usedPoint, payable);
        var ready = kakaoApiService.readyCart(order);

        return Map.of(
                "next_redirect_pc_url", ready.get("next_redirect_pc_url"),
                "orderId", order.getOrderId()
        );
    }

    /* ================= 승인/취소/실패 콜백 ================= */
    @GetMapping("/success")
    public String success(@RequestParam("pg_token") String pgToken,
                          @RequestParam("orderId") Long orderId,
                          Principal principal,
                          Model model) {
        try {
            String userId = (principal != null ? principal.getName() : "GUEST");
            var latest = paymentService.getLatestByOrderId(orderId);
            var result = kakaoApiService.approve(latest.getTid(), String.valueOf(orderId), userId, pgToken);
            orderService.markPaid(orderId);

            model.addAttribute("orderId", orderId);
            model.addAttribute("pay", result);
            return "pay/success-popup";
        } catch (Exception e) {
            model.addAttribute("orderId", orderId);
            model.addAttribute("error", e.getMessage());
            return "pay/fail-popup";
        }
    }

    @GetMapping("/cancel")
    public String cancel(@RequestParam("orderId") Long orderId, Model model) {
        model.addAttribute("orderId", orderId);
        return "pay/cancel-popup";
    }

    @GetMapping("/fail")
    public String fail(@RequestParam("orderId") Long orderId, Model model) {
        model.addAttribute("orderId", orderId);
        return "pay/fail-popup";
    }

    /* ================= 팝업 닫힘 시 상태 처리/조회 ================= */
    @PostMapping("/cancel-pending")
    @ResponseBody
    public void cancelPendingInternal(@RequestParam("orderId") Long orderId) {
        Order o = orderService.get(orderId);
        if (o == null) return;
        if ("PAID".equalsIgnoreCase(o.getStatus())) return;
        if ("PENDING".equalsIgnoreCase(o.getStatus())) orderService.markCanceled(orderId);
    }

    @GetMapping("/order/{orderId}/status")
    @ResponseBody
    public Map<String, String> getStatus(@PathVariable("orderId") Long orderId) {
        Order o = orderService.get(orderId);
        return Map.of("status", o.getStatus());
    }

    /* ================= 결제 취소(유저 요청) ================= */
    @PostMapping("/cancel-paid/{targetOrderId}")
    @ResponseBody
    public Map<String, Object> cancelPaid(@PathVariable("targetOrderId") Long orderId,
    									  @RequestParam(value="reason", required=false) String reason) {
        var result = payCancelService.cancelPaid(orderId, reason);
        return Map.of("ok", true, "pg", result);
    }
}
