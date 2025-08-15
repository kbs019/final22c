// // src/main/java/com/ex/final22c/service/KakaoApiService.java
// package com.ex.final22c.service;

// import com.ex.final22c.data.order.Order;              // ✅ 주문 엔티티
// import com.ex.final22c.data.perfume.Perfume;
// import com.ex.final22c.service.payment.PaymentService;
// import com.ex.final22c.service.perfume.PerfumeService;
// import lombok.RequiredArgsConstructor;
// import org.springframework.boot.web.client.RestTemplateBuilder;
// import org.springframework.http.HttpEntity;
// import org.springframework.http.HttpHeaders;
// import org.springframework.http.MediaType;
// import org.springframework.http.ResponseEntity;
// import org.springframework.stereotype.Service;
// import org.springframework.web.client.HttpStatusCodeException;
// import org.springframework.web.client.RestTemplate;

// import java.time.Duration;
// import java.time.LocalDateTime;
// import java.util.LinkedHashMap;
// import java.util.Map;

// @Service
// @RequiredArgsConstructor
// public class KakaoApiService {

//     private final PerfumeService perfumeService;
//     private final PaymentService paymentService;

//     // DEV 시크릿키 (운영에서는 환경변수/설정파일 사용!)
//     private final String secretKey = "DEV42DBFD224729F907C9585329C415AD93AC91B";
//     private static final String HOST = "https://open-api.kakaopay.com/online/v1/payment";

//     /**
//      * 단건 결제 Ready
//      * - 컨트롤러에서 먼저 PENDING 주문(Order)을 생성한 뒤, 그 Order를 넘겨준다.
//      * - 서버가 가격/수량 검증 및 합계를 계산한다.
//      * - Ready 응답의 tid를 JPA로 Payment(READY) 저장한다.
//      */
//     public Map<String, Object> readySingle(int perfumeNo, int qty, String userId, Order order) {
//         Perfume p = perfumeService.getPerfume(perfumeNo);
//         if (p == null) throw new IllegalArgumentException("상품 없음: " + perfumeNo);

//         int stock   = Math.max(p.getCount(), 0);
//         int safeQty = Math.min(Math.max(qty, 1), Math.max(stock, 1));
//         if (stock == 0) throw new IllegalStateException("품절 상품입니다.");

//         long unit  = p.getSellPrice();
//         long total = unit * safeQty;

//         // ★ 총액 0 이하 방어 (카카오 total_amount는 1 이상이어야 함)
//         if (total <= 0) {
//             throw new IllegalStateException("결제 총액이 0원 이하입니다. unit=" + unit + ", qty=" + safeQty);
//         }

//         String partnerOrderId = String.valueOf(order.getOrderId());
//         String partnerUserId  = (userId != null ? userId : "GUEST");

//         HttpHeaders headers = new HttpHeaders();
//         headers.set("Authorization", "SECRET_KEY " + secretKey); // DEV Open API
//         headers.setContentType(MediaType.APPLICATION_JSON);
//         headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));

//         Map<String, Object> body = new LinkedHashMap<>();
//         body.put("cid", "TC0ONETIME");                       // 테스트 CID
//         body.put("partner_order_id", partnerOrderId);
//         body.put("partner_user_id", partnerUserId);
//         body.put("item_name", p.getPerfumeName());
//         body.put("quantity", safeQty);
//         body.put("total_amount", total);
//         body.put("tax_free_amount", 0);                      // ★ 비과세 없음 → 0 고정
//         // body.put("vat_amount", ...);                      // 보내지 않음(자동계산)

//         body.put("approval_url", "http://localhost:8080/pay/success?orderId=" + partnerOrderId);
//         body.put("cancel_url",   "http://localhost:8080/pay/cancel");
//         body.put("fail_url",     "http://localhost:8080/pay/fail");

//         // ★ 디버그 로그 (최종 값 확인)
//         System.out.printf("[KAKAO READY] total=%d, taxFree=%d, qty=%d, unit=%d, orderId=%s, user=%s%n",
//                 total, 0, safeQty, unit, partnerOrderId, partnerUserId);

//         RestTemplate rt = new RestTemplateBuilder()
//                 .setConnectTimeout(Duration.ofSeconds(5))
//                 .setReadTimeout(Duration.ofSeconds(5))
//                 .build();

//         try {
//             ResponseEntity<Map<String, Object>> res = rt.postForEntity(
//                     HOST + "/ready", new HttpEntity<>(body, headers), (Class<Map<String, Object>>)(Class<?>)Map.class);

//             Map<String, Object> result = res.getBody();
//             String tid = (result != null) ? String.valueOf(result.get("tid")) : null;

//             paymentService.saveReady(order, (int) total, tid); // READY 저장
//             return result;

//         } catch (HttpStatusCodeException e) {
//             // 서버 로그에 바디 찍어보기
//             System.err.println("[KAKAO READY ERROR] " + e.getResponseBodyAsString());
//             throw new IllegalStateException("KakaoPay READY 실패: " + e.getResponseBodyAsString(), e);
//         }
//     }

//     /**
//      * 결제 승인(Approve)
//      * - tid는 DB(Payment)에서 조회 가능. 컨트롤러에서 orderId로 Payment를 찾아 tid를 넘겨주거나,
//      *   바로 이 메서드에 tid와 partnerOrderId/partnerUserId를 전달해도 된다.
//      */
//     public Map<String, Object> approve(String tid, String partnerOrderId,
//                                        String partnerUserId, String pgToken) {
//         HttpHeaders headers = new HttpHeaders();
//         headers.set("Authorization", "SECRET_KEY " + secretKey);
//         headers.setContentType(MediaType.APPLICATION_JSON);

//         Map<String, Object> body = new LinkedHashMap<>();
//         body.put("cid", "TC0ONETIME");
//         body.put("tid", tid);
//         body.put("partner_order_id", partnerOrderId);
//         body.put("partner_user_id", partnerUserId);
//         body.put("pg_token", pgToken);

//         RestTemplate rt = new RestTemplateBuilder()
//                 .setConnectTimeout(Duration.ofSeconds(5))
//                 .setReadTimeout(Duration.ofSeconds(5))
//                 .build();

//         try {
//             ResponseEntity<Map<String, Object>> res = rt.postForEntity(
//                     HOST + "/approve", new HttpEntity<>(body, headers), (Class<Map<String, Object>>)(Class<?>)Map.class);

//             Map<String, Object> result = res.getBody();

//             // 승인 성공 저장 (aid, approved_at)
//             if (result != null) {
//                 String aid = String.valueOf(result.get("aid"));
//                 String approvedAtStr = String.valueOf(result.get("approved_at")); // e.g. 2025-08-13T12:34:56+09:00
//                 // 간단 파싱(초 이하/타임존 제거)
//                 LocalDateTime approvedAt = LocalDateTime.parse(approvedAtStr.substring(0, 19));
//                 paymentService.markSuccess(tid, aid, approvedAt);
//             }
//             return result;

//         } catch (HttpStatusCodeException e) {
//             throw new IllegalStateException("KakaoPay APPROVE 실패: " + e.getResponseBodyAsString(), e);
//         }
//     }
// }
