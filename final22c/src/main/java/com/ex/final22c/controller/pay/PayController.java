
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

@RestController
@RequiredArgsConstructor
@RequestMapping("/pay")
public class PayController {

    private final OrderService orderService;
    private final KakaoApiService kakaoApiService;
    private final PaymentService paymentService;

    @PostMapping("/ready")
    public Map<String, Object> ready(
            @RequestParam(name = "productId") long ProductId, // 이름 명시
            @RequestParam(name = "qty")       int qty,
            Principal principal
    ) {  // ★ 여기: 닫는 괄호 한 번만, 그리고 중괄호 열기

        if (principal == null) {
            // 테스트만 급하면 아래 두 줄 사용해도 됨
            // Order order = orderService.createPendingOrderForTest(1L, perfumeNo, qty);
            // return kakaoApiService.readySingle(perfumeNo, qty, "TEST_USER", order, 0);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        }

        String userId = principal.getName();
        Order order = orderService.createPendingOrder(userId, ProductId, qty);
        return kakaoApiService.readySingle(ProductId, qty, userId, order);
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
