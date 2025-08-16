package com.ex.final22c.controller.pay;

import com.ex.final22c.data.order.Order;
import com.ex.final22c.data.payment.Payment;
import com.ex.final22c.service.KakaoApiService;
import com.ex.final22c.service.order.OrderService;
import com.ex.final22c.service.payment.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.Map;

@Controller
@RequiredArgsConstructor
@RequestMapping("/pay")
public class PayController {

    private final OrderService orderService;
    private final KakaoApiService kakaoApiService;
    private final PaymentService paymentService;

    @PostMapping("/ready")
    @ResponseBody
    public Map<String, Object> ready(
            @RequestParam("productId") long productId,
            @RequestParam("qty")       int qty,
            @RequestParam("usedPoint") int usedPoint,
            Principal principal
    ) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        }
        final String userId = principal.getName();

        // usedPoint 정규화 + payable 계산/저장
        Order order = orderService.createPendingOrder(userId, productId, qty, usedPoint);

        // Kakao Ready: total_amount는 order.getTotalAmount()를 KakaoApiService에서 사용
        return kakaoApiService.readySingle(productId, qty, userId, order);
    }

    /** 승인 콜백(팝업) */
    @GetMapping("/success")
    public String success(@RequestParam("pg_token") String pgToken,
                          @RequestParam("orderId") Long orderId,
                          Principal principal,
                          Model model) {

        String userId = (principal != null ? principal.getName() : "GUEST");

        Payment pay = paymentService.getLatestByOrderId(orderId);

        Map<String, Object> result = kakaoApiService.approve(
                pay.getTid(),
                String.valueOf(orderId),
                userId,
                pgToken
        );

        // 승인 완료 → 주문 상태 PAID로 마킹
        orderService.markPaid(orderId);

        model.addAttribute("orderId", orderId);
        model.addAttribute("pay", result);
        return "pay/success-popup"; // postMessage 후 스스로 닫히는 뷰
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

    /** 팝업 닫힘 등으로 부모창이 호출하는 내부 취소(미승인 상태만) */
    @PostMapping("/cancel-pending")
    @ResponseBody
    public void cancelPendingInternal(@RequestParam("orderId") Long orderId) {
        Order o = orderService.get(orderId);
        if (o == null) return;
        if ("PAID".equalsIgnoreCase(o.getStatus())) return;     // 이미 결제됨 → 무시
        if ("PENDING".equalsIgnoreCase(o.getStatus())) {
            orderService.markCanceled(orderId);                 // PENDING → CANCELED
        }
    }

    /** 부모창이 팝업 닫힘 후 상태 확인할 때 사용 */
    @GetMapping("/order/{orderId}/status")
    @ResponseBody
    public Map<String, String> getStatus(@PathVariable Long orderId) {
        Order o = orderService.get(orderId);
        return Map.of("status", o.getStatus());
    }
}
