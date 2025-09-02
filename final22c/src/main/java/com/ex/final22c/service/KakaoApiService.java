package com.ex.final22c.service;

import com.ex.final22c.data.order.Order;              
import com.ex.final22c.data.product.Product;
import com.ex.final22c.service.payment.PaymentService;
import com.ex.final22c.service.product.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class KakaoApiService {

    private final ProductService productService;
    private final PaymentService paymentService;

    private final String secretKey = "DEV42DBFD224729F907C9585329C415AD93AC91B";
    private static final String HOST = "https://open-api.kakaopay.com/online/v1/payment";

    /**
     * 단건 결제 Ready
     * - OrderService에서 이미 usedPoint/배송비(상수 3000) 반영된 totalAmount를 계산해 Order에 저장.
     * - 여기서는 그 스냅샷을 그대로 카카오 total_amount로 보낸다.
     */
    public Map<String, Object> readySingle(long productId, int qty, String userId, Order order) {
        if (order == null) throw new IllegalArgumentException("order가 null입니다.");
        int payable = order.getTotalAmount();
        if (payable <= 0) throw new IllegalStateException("결제 총액이 0원 이하입니다.");

        Product p = productService.getProduct(productId);
        if (p == null) throw new IllegalArgumentException("상품 없음: " + productId);

        int safeQty = Math.max(1, qty);
        String partnerOrderId = String.valueOf(order.getOrderId());
        String partnerUserId  = (userId != null ? userId : "GUEST");

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "SECRET_KEY " + secretKey);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("cid", "TC0ONETIME");
        body.put("partner_order_id", partnerOrderId);
        body.put("partner_user_id", partnerUserId);
        body.put("item_name", p.getName());
        body.put("quantity", safeQty);
        body.put("total_amount", payable);
        body.put("tax_free_amount", 0);

        String base = "http://localhost:8080";
        body.put("approval_url", base + "/pay/success?orderId=" + partnerOrderId);
        body.put("cancel_url",   base + "/pay/cancel?orderId="  + partnerOrderId);
        body.put("fail_url",     base + "/pay/fail?orderId="    + partnerOrderId);

        RestTemplate rt = new RestTemplateBuilder()
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(5))
                .build();

        try {
            ResponseEntity<Map<String, Object>> res = rt.postForEntity(
                    HOST + "/ready", new HttpEntity<>(body, headers), (Class<Map<String, Object>>)(Class<?>)Map.class);

            Map<String, Object> result = res.getBody();
            if (result == null) result = new LinkedHashMap<>();

            String tid = (String) result.get("tid");
            paymentService.saveReady(order, payable, tid);

            // 프런트가 팝업 닫힘 감시에 쓰도록 orderId를 함께 반환
            result.put("orderId", order.getOrderId());
            return result;

        } catch (HttpStatusCodeException e) {
            System.err.println("[KAKAO READY ERROR] " + e.getResponseBodyAsString());
            throw new IllegalStateException("KakaoPay READY 실패: " + e.getResponseBodyAsString(), e);
        }
    }


    /** 결제 승인 */
    public Map<String, Object> approve(String tid, String partnerOrderId,
                                       String partnerUserId, String pgToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "SECRET_KEY " + secretKey);
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("cid", "TC0ONETIME");
        body.put("tid", tid);
        body.put("partner_order_id", partnerOrderId);
        body.put("partner_user_id", partnerUserId);
        body.put("pg_token", pgToken);

        RestTemplate rt = new RestTemplateBuilder()
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(5))
                .build();

        try {
            ResponseEntity<Map<String, Object>> res = rt.postForEntity(
                    HOST + "/approve", new HttpEntity<>(body, headers), (Class<Map<String, Object>>)(Class<?>)Map.class);

            Map<String, Object> result = res.getBody();

            if (result != null) {
                String aid = String.valueOf(result.get("aid"));
                String approvedAtStr = String.valueOf(result.get("approved_at"));
                LocalDateTime approvedAt = LocalDateTime.parse(approvedAtStr.substring(0, 19));
                paymentService.markSuccess(tid, aid, approvedAt);
            }
            return result;

        } catch (HttpStatusCodeException e) {
            throw new IllegalStateException("KakaoPay APPROVE 실패: " + e.getResponseBodyAsString(), e);
        }
    }
    
    // 결제 취소
    public Map<String, Object> cancel(String tid, int cancelAmount, String reason){
    	HttpHeaders headers = new HttpHeaders();
    	headers.set("Authorization", "SECRET_KEY " + secretKey);
    	headers.setContentType(MediaType.APPLICATION_JSON);
    	headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
    	
    	Map<String, Object> body = new LinkedHashMap<>();
    	body.put("cid", "TC0ONETIME");	// 테스트 버전
    	body.put("tid", tid);			// 결제 고유번호
    	body.put("cancel_amount", cancelAmount);	// 취소 금액
    	body.put("cancel_tax_free_amount", 0);	// 면세금액 없어도 써줌..
    	if(reason != null && !reason.isBlank()) {
    		body.put("payload", reason);	// 취소 사유
    	}
    	
    	RestTemplate rt = new RestTemplateBuilder()
    			.setConnectTimeout(Duration.ofSeconds(5))
    			.setReadTimeout(Duration.ofSeconds(5))
    			.build();
    	try {
    		ResponseEntity<Map<String, Object>> res = rt.postForEntity(
    				HOST + "/cancel",
    				new HttpEntity<>(body, headers),
    				(Class<Map<String, Object>>)(Class<?>)Map.class
    		);
    		Map<String, Object> result = res.getBody();
    		return(result != null) ? result : new LinkedHashMap<>();
    	}catch(HttpStatusCodeException e) {
    		 System.err.println("[KAKAO CANCEL ERROR] " + e.getResponseBodyAsString());
    	        throw new IllegalStateException("KakaoPay CANCEL 실패: " + e.getResponseBodyAsString(), e);
    	}
    }
    // 장바구니 다건 
    public Map<String, Object> readyCart(Order order) {
        if (order == null) throw new IllegalArgumentException("order가 null입니다.");
        int payable = order.getTotalAmount();
        if (payable <= 0) throw new IllegalStateException("결제 총액이 0원 이하입니다.");

        String partnerOrderId = String.valueOf(order.getOrderId());
        String partnerUserId  = (order.getUser() != null ? order.getUser().getUserName() : "GUEST");

        // item_name: "첫상품명 외 N건"
        String itemName;
        int size = (order.getDetails() != null ? order.getDetails().size() : 0);
        if (size == 0) {
            itemName = "장바구니";
        } else {
            String first = order.getDetails().get(0).getProduct().getName();
            itemName = (size > 1) ? first + " 외 " + (size - 1) + "건" : first;
        }

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "SECRET_KEY " + secretKey);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("cid", "TC0ONETIME");
        body.put("partner_order_id", partnerOrderId);
        body.put("partner_user_id", partnerUserId);
        body.put("item_name", itemName);
        body.put("quantity", 1);                // 장바구니는 합산 1건으로 처리
        body.put("total_amount", payable);      // = itemsTotal + 3000 - usedPoint (스냅샷)
        body.put("tax_free_amount", 0);

        String base = "http://localhost:8080";
        body.put("approval_url", base + "/pay/success?orderId=" + partnerOrderId);
        body.put("cancel_url",   base + "/pay/cancel?orderId="  + partnerOrderId);
        body.put("fail_url",     base + "/pay/fail?orderId="    + partnerOrderId);

        RestTemplate rt = new RestTemplateBuilder()
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(5))
                .build();

        try {
            ResponseEntity<Map<String, Object>> res = rt.postForEntity(
                    HOST + "/ready",
                    new HttpEntity<>(body, headers),
                    (Class<Map<String, Object>>)(Class<?>)Map.class
            );

            Map<String, Object> result = res.getBody();
            if (result == null) result = new LinkedHashMap<>();

            String tid = (String) result.get("tid");
            // PENDING 상태의 결제 레코드 저장 (단건과 동일)
            paymentService.saveReady(order, payable, tid);

            result.put("orderId", order.getOrderId());
            return result;

        } catch (HttpStatusCodeException e) {
            System.err.println("[KAKAO READY CART ERROR] " + e.getResponseBodyAsString());
            throw new IllegalStateException("KakaoPay READY 실패: " + e.getResponseBodyAsString(), e);
        }
    }
    
    
    
    
    
    
    
}
