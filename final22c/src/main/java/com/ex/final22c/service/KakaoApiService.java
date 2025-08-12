package com.ex.final22c.service;

import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Service
public class KakaoApiService {

    public Map<String, Object> kakaoPay(Map<String, Object> params) {

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "SECRET_KEY DEV42DBFD224729F907C9585329C415AD93AC91B"); // dev key
        headers.setContentType(MediaType.APPLICATION_JSON);

        // 결제 요청 JSON 데이터
        Map<String, Object> payParams = new HashMap<>();
        payParams.put("cid", "TC0ONETIME");
        payParams.put("partner_order_id", "ORDER-0001");
        payParams.put("partner_user_id", "user123");
        payParams.put("item_name", params.get("item_name"));
        payParams.put("quantity", params.get("quantity"));
        payParams.put("total_amount", params.get("total_amount"));
        payParams.put("tax_free_amount", params.get("tax_free_amount"));
        payParams.put("approval_url", "http://localhost:8080/pay/success");
        payParams.put("cancel_url", "http://localhost:8080/pay/cancel");
        payParams.put("fail_url", "http://localhost:8080/pay/fail");

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(payParams, headers);

        RestTemplate template = new RestTemplate();
        String url = "https://open-api.kakaopay.com/online/v1/payment/ready"; // dev endpoint

        // 요청 및 응답
        ResponseEntity<Map> response = template.postForEntity(url, request, Map.class);
        return response.getBody(); // 응답 JSON 그대로 반환
    }

}
