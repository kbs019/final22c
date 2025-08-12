package com.ex.final22c.controller.kakao;

import com.ex.final22c.service.KakaoApiService;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@Controller
@RequestMapping("/pay")
public class KakaoPayController {

    private final KakaoApiService payService;

    public KakaoPayController(KakaoApiService payService) {
        this.payService = payService;
    }
    // 결제 준비 요청
    @GetMapping("/ready")
    @ResponseBody
    public Map<String, Object> kakaoPay(@RequestParam Map<String, Object> params) {
        return payService.kakaoPay(params); // JSON 응답 그대로 리턴
    }

    // 결제 테스트 페이지
    @GetMapping("/test")
    public String testPay() {
        return "pay/test"; // templates/pay/test.html
    }
    
    @GetMapping("/pay/success")
    public String paySuccess() {
        return "결제가 성공적으로 완료되었습니다.";
    }

    @GetMapping("/pay/cancel")
    public String payCancel() {
        return "결제가 취소되었습니다.";
    }

    @GetMapping("/pay/fail")
    public String payFail() {
        return "결제에 실패하였습니다.";
    }
}
