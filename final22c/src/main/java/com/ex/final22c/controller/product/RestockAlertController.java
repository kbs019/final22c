package com.ex.final22c.controller.product;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.ex.final22c.data.user.Users;
import com.ex.final22c.repository.user.UserRepository;
import com.ex.final22c.service.product.RestockAlertService;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
@RequestMapping("/restockAlert")
public class RestockAlertController {

    private final RestockAlertService restockAlertService;
    private final UserRepository userRepository;

    @PostMapping("/subscribe")
    public ResponseEntity<Map<String, Object>> subscribe(
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal UserDetails principal) {

        // 1) 로그인 확인
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // 2) productId 파싱(숫자/문자 모두 수용)
        Long productId = null;
        Object v = body.get("productId");
        if (v instanceof Number n) {
            productId = n.longValue();
        } else if (v instanceof String s && !s.isBlank()) {
            try {
                productId = Long.parseLong(s);
            } catch (NumberFormatException ignore) {
            }
        }
        if (productId == null) {
            Map<String, Object> res = new HashMap<>();
            res.put("ok", false);
            res.put("message", "productId가 필요합니다.");
            return ResponseEntity.badRequest().body(res);
        }

        // 3) 사용자 조회(Optional 처리)
        String userName = principal.getUsername();
        Optional<Users> opt = userRepository.findByUserName(userName);
        if (opt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        Users me = opt.get();

        // 4) 구독 생성(중복 REQUESTED는 서비스에서 걸러짐)
        restockAlertService.subscribe(productId, me.getUserNo(), me.getPhone());

        Map<String, Object> res = new HashMap<>();
        res.put("ok", true);
        return ResponseEntity.ok(res);
    }

    // 이미 신청했는지 확인
    @GetMapping("/state")
    public ResponseEntity<Map<String, Object>> state(
            @AuthenticationPrincipal UserDetails principal,
            @RequestParam("productId") Long productId) {

        if (principal == null)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        Optional<Users> meOpt = userRepository.findByUserName(principal.getUsername());
        if (meOpt.isEmpty())
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        boolean subscribed = restockAlertService.isRequested(productId, meOpt.get().getUserNo());
        Map<String, Object> res = new HashMap<>();
        res.put("subscribed", subscribed);
        return ResponseEntity.ok(res);
    }

    // 신청 취소
    @PostMapping("/cancel") // 클래스 레벨이 @RequestMapping("/restockAlert") 이면 최종 경로: /restockAlert/cancel
    public ResponseEntity<Map<String, Object>> cancel(
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal UserDetails principal) {

        if (principal == null)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        Long productId = asLong(body.get("productId"));
        if (productId == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "ok", false,
                    "message", "productId가 필요합니다."));
        }

        Optional<Users> meOpt = userRepository.findByUserName(principal.getUsername());
        if (meOpt.isEmpty())
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        boolean changed = restockAlertService.cancel(productId, meOpt.get().getUserNo()); // 둘 다 Long

        return ResponseEntity.ok(Map.of("ok", true, "changed", changed));
    }


    // 헬퍼
    private static Long asLong(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.longValue();         // JSON 숫자 케이스
        if (v instanceof String s) {
            s = s.trim();
            if (s.isEmpty()) return null;
            try { return Long.valueOf(s); } catch (NumberFormatException ignore) { return null; }
        }
        return null;
    }
}
