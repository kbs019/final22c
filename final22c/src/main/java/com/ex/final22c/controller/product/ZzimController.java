package com.ex.final22c.controller.product;

import java.security.Principal;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import com.ex.final22c.service.product.ZzimService;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
@RequestMapping("/zzim")
public class ZzimController {

    private final ZzimService zzimService;

    /** 상태 조회: { zzimed: true|false } */
    @GetMapping("/state")
    @ResponseBody
    public ResponseEntity<?> state(@RequestParam("productId") Long productId,
                                   Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "로그인이 필요합니다."));
        }
        boolean zzimed = zzimService.isZzimed(principal.getName(), productId);
        return ResponseEntity.ok(Map.of("zzimed", zzimed));
    }

    /** 등록: { ok: true } (멱등)
     *  - JSON body { "productId": 1 } 또는 쿼리스트링 ?productId=1 둘 다 지원
     */
    @PostMapping("/add")
    @ResponseBody
    public ResponseEntity<?> add(@RequestParam(value = "productId", required = false) Long productIdParam,
                                @RequestBody(required = false) Map<String, Object> body,
                                Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "로그인이 필요합니다."));
        }
        Long productId = extractProductId(productIdParam, body);
        if (productId == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "productId가 누락되었습니다."));
        }
        zzimService.add(principal.getName(), productId);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    /** 해제: { ok: true } (멱등) */
    @PostMapping("/remove")
    @ResponseBody
    public ResponseEntity<?> remove(@RequestParam("productId") Long productId,
                                    Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "로그인이 필요합니다."));
        }
        zzimService.remove(principal.getName(), productId);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    /** DTO 없이 바디/파라미터에서 productId 추출 */
    private Long extractProductId(Long productIdParam, Map<String, Object> body) {
        if (productIdParam != null) return productIdParam;
        if (body == null) return null;
        Object v = body.get("productId");
        if (v instanceof Number) return ((Number) v).longValue();
        if (v instanceof String s) {
            try { return Long.parseLong(s); } catch (NumberFormatException ignored) {}
        }
        return null;
    }
}