// src/main/java/com/ex/final22c/controller/pay/PayController.java
package com.ex.final22c.controller.pay;

import com.ex.final22c.data.order.Order;
import com.ex.final22c.data.payment.Payment;
import com.ex.final22c.service.KakaoApiService;
import com.ex.final22c.service.order.OrderService;
import com.ex.final22c.service.payment.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.Map;

@Controller
@RequiredArgsConstructor
@RequestMapping("/pay")
public class PayController {

    private final KakaoApiService kakaoApiService;
    private final PaymentService paymentService;
    private final OrderService orderService;

    /**
     * 결제 준비(Ready)
     * 프런트는 perfumeNo, qty만 보냄.
     * 1) PENDING 주문 생성 → 2) 카카오 Ready 호출 → 3) Payment(READY) 저장
     */
    @PostMapping("/ready")
    @ResponseBody
    public Map<String, Object> ready(@RequestParam int perfumeNo,
                                     @RequestParam int qty,
                                     Principal principal) {
        String userId = (principal != null ? principal.getName() : "GUEST");

        // 1) 주문 먼저 생성(PENDING)
        Order order = orderService.createPendingOrder(userId, perfumeNo, qty);

        // 2) Ready + 3) Payment READY 저장(JPA)
        return kakaoApiService.readySingle(perfumeNo, qty, userId, order);
    }

    /**
     * 결제 승인(Approve) 콜백
     * approval_url에 orderId를 쿼리로 붙여두었기 때문에, 여기서 orderId로 Payment를 조회해 tid를 얻는다.
     */
    @GetMapping("/success")
    public String success(@RequestParam("pg_token") String pgToken,
                          @RequestParam("orderId") Long orderId,
                          Principal principal,
                          Model model) {

        String userId = (principal != null ? principal.getName() : "GUEST");

        // Ready 때 저장해 둔 Payment(READY)를 orderId로 가져옴
        // ※ PaymentService에 getLatestByOrderId(or getByOrderId) 메서드가 있어야 합니다.
        Payment pay = paymentService.getLatestByOrderId(orderId);

        // 카카오 승인 호출
        Map<String, Object> result = kakaoApiService.approve(
                pay.getTid(),
                String.valueOf(orderId),
                userId,
                pgToken
        );

        model.addAttribute("pay", result);
        return "pay/success";
    }

    @GetMapping("/cancel")
    public String cancel() {
        return "pay/cancel";
    }

    @GetMapping("/fail")
    public String fail() {
        return "pay/fail";
    }
}
