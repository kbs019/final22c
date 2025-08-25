package com.ex.final22c.controller.pay;

import java.security.Principal;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
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
import com.ex.final22c.data.payment.Payment;
import com.ex.final22c.data.payment.dto.PayCartRequest;
import com.ex.final22c.data.payment.dto.PaySingleRequest;
import com.ex.final22c.data.payment.dto.ShipSnapshotReq;
import com.ex.final22c.data.user.Users;
import com.ex.final22c.repository.order.OrderRepository;
import com.ex.final22c.repository.payment.PaymentRepository;
import com.ex.final22c.service.KakaoApiService;
import com.ex.final22c.service.cart.CartService;
import com.ex.final22c.service.order.MyOrderService;
import com.ex.final22c.service.order.OrderService;
import com.ex.final22c.service.payment.PayCancelService;
import com.ex.final22c.service.payment.PaymentService;
import com.ex.final22c.service.refund.RefundService;
import com.ex.final22c.service.user.UsersService;
import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
@RequestMapping("/pay")
public class PayController {

    private final OrderService orderService;
    private final KakaoApiService kakaoApiService;
    private final PaymentService paymentService;
    private final CartService cartService;
    private final RefundService refundService;
    private final MyOrderService myOrderService;
    private final UsersService usersService;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    
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
        final ShipSnapshotReq ship = req.ship();

        // 1. 주문 생성 (PENDING) 으로
        Order order = orderService.createPendingOrder(userId, productId, qty, usedPoint, ship);

        // 2. 결제액 계산
        int itemsTotal = order.getTotalAmount();
        int shipping   = SHIPPING_FEE;
        int payable    = Math.max(0, itemsTotal + shipping - usedPoint);
        
        // 3) 총 결제금액이 0원일때 PG호출 스킵
        if(payable == 0) {
        	// 결제레코드
        	paymentService.recordZeroPayment(order.getOrderId());
        	// 주문 상태를 PAID로 전환 + 마일리지 차감
        	 orderService.markPaid(order.getOrderId());
        	 return Map.of(
        	            // 프론트가 기존 로직을 그대로 쓰도록 성공 URL을 돌려줌(새 엔드포인트)
        	            "next_redirect_pc_url", "/pay/success-zero?orderId=" + order.getOrderId(),
        	            "orderId", order.getOrderId()
        	        );
        }  
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
        final ShipSnapshotReq ship = req.ship();

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

        Order order = orderService.createCartPendingOrder(userId, lines, itemsTotal, shipping, usedPoint, payable, ship);
        // 0원 결제 
        if (payable == 0) {
            paymentService.recordZeroPayment(order.getOrderId());
            orderService.markPaid(order.getOrderId());

            return Map.of(
                "next_redirect_pc_url", "/pay/success-zero?orderId=" + order.getOrderId(),
                "orderId", order.getOrderId()
            );
        }
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
    // 0 원 결제시 
    @GetMapping("/success-zero")
    public String successZero(@RequestParam("orderId") Long orderId,
                              Principal principal,
                              Model model) {
        // 여기서는 kakaoApiService.approve 호출 금지
        Order order = orderService.get(orderId);
        if (order == null) {
            model.addAttribute("orderId", orderId);
            model.addAttribute("error", "order not found");
            return "pay/fail-popup";
        }
        // 이미 markPaid 되어 있어야 함(이중호출 방지 차원에서 한번 더 안전 체크 가능)
        model.addAttribute("orderId", orderId);
        // 뷰에서 보여줄 임시 결제정보(마일리지결제 표기)
        model.addAttribute("pay", Map.of(
            "method_type", "POINT",
            "approved_at", java.time.LocalDateTime.now().toString(),
            "amount", Map.of("total", 0, "point", order.getUsedPoint())
        ));
        return "pay/success-popup";
    }


    @GetMapping("/cancel")
    public String cancel(@RequestParam("orderId") Long orderId, Model model) {
    	orderService.markFailedPending(orderId);
        model.addAttribute("orderId", orderId);
        return "pay/cancel-popup";
    }

    @GetMapping("/fail")
    public String fail(@RequestParam("orderId") Long orderId, Model model) {
    	orderService.markFailedPending(orderId) ; 
        model.addAttribute("orderId", orderId);
        return "pay/fail-popup";
    }

    /* ================= 팝업 닫힘 시 상태 처리/조회 ================= */
    @PostMapping("/cancel-pending")
    @ResponseBody
    public Map<String, Object> cancelPendingInternal(@RequestParam("orderId") Long orderId) {
        Order o = orderService.get(orderId);
        if (o == null) {
            return Map.of("ok", false, "message", "order not found");
        }
        if ("PAID".equalsIgnoreCase(o.getStatus())) {
            return Map.of("ok", false, "message", "already paid");
        }
        if ("PENDING".equalsIgnoreCase(o.getStatus())) {
            orderService.markFailedPending(orderId);
            return Map.of("ok", true, "status", "FAILED");
        }
        return Map.of("ok", true, "status", o.getStatus());
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
    @Transactional
    public Map<String, Object> cancelPaid(@PathVariable("targetOrderId") Long orderId,
            							  @RequestParam(value = "reason", required = false) String reason) {
        // 0) 주문/결제 조회 (details fetch-join)
        Order order = orderRepository.findOneWithDetails(orderId)
            .orElseThrow(() -> new IllegalArgumentException("주문 없음: " + orderId));
        Payment pay = paymentRepository.findTopByOrder_OrderIdOrderByPaymentIdDesc(orderId)
            .orElseThrow(() -> new IllegalArgumentException("결제 정보 없음(orderId): " + orderId));

        // 1) 멱등성/상태 검증
        if ("CANCELED".equalsIgnoreCase(order.getStatus())) {
            return Map.of("alreadyCanceled", true);
        }
        if (!"PAID".equalsIgnoreCase(order.getStatus())) {
            throw new IllegalStateException("취소 불가 상태(주문이 PAID 아님)");
        }
        if (!"SUCCESS".equalsIgnoreCase(pay.getStatus())) {
            throw new IllegalStateException("취소 불가 상태(결제가 SUCCESS 아님)");
        }

        // 2) 0원 결제/PG 미사용  → PG 호출 금지
        if (pay.getAmount() == 0) {
            // 마일리지/재고/상태 롤백 (총액 기준으로 처리하는 쪽이 안전)
            orderService.applyCancel(order, order.getTotalAmount(), reason);

            // 결제 상태도 취소로 표시 (tid 사용 금지)
            paymentService.markCanceledByOrderId(orderId);

            return Map.of("ok", true, "message", "0원 결제건 환불 완료(마일리지 복구)");
        }

        // 3) PG 취소 (전액취소만 지원)
        final int amountToCancel = order.getTotalAmount();
        Map<String, Object> pgResp = kakaoApiService.cancel(pay.getTid(), amountToCancel, reason);

        // 4) DB 취소 처리
        orderService.applyCancel(order, amountToCancel, reason);

        // 5) 결제 상태도 CANCELED로 표시 (tid 기반)
        paymentService.markCanceledByTid(pay.getTid());

        return pgResp;
    }


    /* ================= 마일리지 적립 ================= */
    @PostMapping("/{orderId}/confirm")
    public ResponseEntity<?> confirm(@PathVariable("orderId") Long orderId, Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("ok", false, "message", "로그인이 필요합니다."));
        }
        try {
            // 1) 확정 + 적립(서비스에서 처리)
            Order order = myOrderService.confirmOrderAndAwardMileage(principal.getName(), orderId);

            // 2) 프론트 노출용 계산값
            int earnBase = Math.max(0, order.getTotalAmount() - order.getUsedPoint());
            int mileage  = (int) Math.floor(earnBase * 0.05);

            // 3) 보유 마일리지 재조회(필드명은 프로젝트에 맞게)
            Users me = usersService.getUser(principal.getName());
            int pointBalance = me.getMileage(); // 또는 getPoint()

            return ResponseEntity.ok(Map.of(
                "ok", true,
                "message", "주문을 확정했어요.",
                "mileage", mileage,
                "pointBalance", pointBalance,
                "orderId", order.getOrderId()
            ));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("ok", false, "message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("ok", false, "message", "처리 중 오류가 발생했습니다."));
        }
    }

    /** order.html에서 모달 "요청하기"가 호출 */
    @PostMapping("/{orderId}/refund")
    public ResponseEntity<?> requestRefund(@PathVariable("orderId") long orderId,
                                           @RequestBody Map<String, Object> body,
                                           Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(401).body(Map.of("message", "로그인이 필요합니다."));
        }

        String reason = String.valueOf(body.getOrDefault("reason", "")).trim();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) body.get("items"); // null 가능

        Long refundId = refundService.requestRefund(orderId, principal.getName(), reason, items);

        return ResponseEntity.ok(Map.of(
            "message", "환불 요청이 접수되었습니다.",
            "refundId", refundId
        ));
    }
}