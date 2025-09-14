package com.ex.final22c.controller.api;



import com.ex.final22c.service.chat.HybridRecommendationService;
import com.ex.final22c.service.product.ProductService;
import com.ex.final22c.data.product.Product;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/recommendation")
@RequiredArgsConstructor
@Slf4j
public class RecommendationController {

    private final HybridRecommendationService hybridRecommendationService;
    private final ProductService productService;

    /**
     * ✅ 설문 기반 AI 분석 요청 (회원/비회원 공통) - JSON 문자열 직접 반환
     */
    @PostMapping("/analyze")
    public ResponseEntity<String> analyze(@RequestBody Map<String, String> survey) {
            String aiJson = hybridRecommendationService.analyzeForGuest(survey);
            return ResponseEntity.ok().body(aiJson);    
	}

    /**
     * ✅ 제품 간단 정보 조회 API (향수 추천에서 사용)
     * @param ids 제품 ID들 (콤마로 구분된 문자열, 예: "3,7")
     * @return 제품들의 기본 정보 리스트
     */
    @GetMapping("/products/brief")
    @ResponseBody
    public ResponseEntity<List<Map<String, Object>>> getProductsBrief(
            @RequestParam("ids") String ids) {
            
        try {
            if (ids == null || ids.trim().isEmpty()) {
                return ResponseEntity.ok(List.of());
            }
            
            // 콤마로 구분된 ID 문자열을 Long 리스트로 변환
            List<Long> productIds = Arrays.stream(ids.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(s -> {
                        try {
                            return Long.parseLong(s);
                        } catch (NumberFormatException e) {
                            return null;
                        }
                    })
                    .filter(id -> id != null)
                    .collect(Collectors.toList());
            
            if (productIds.isEmpty()) {
                return ResponseEntity.ok(List.of());
            }
            
            List<Map<String, Object>> products = new ArrayList<>();
            
            for (Long productId : productIds) {
                try {
                    Product product = productService.getProduct(productId);
                    
                    Map<String, Object> briefInfo = new HashMap<>();
                    briefInfo.put("id", product.getId());
                    briefInfo.put("name", product.getName());
                    briefInfo.put("brandName", product.getBrand() != null ? product.getBrand().getBrandName() : "");
                    briefInfo.put("price", product.getSellPrice());
                    
                    // 이미지 URL 생성
                    String imageUrl = "/img/noimg.png";
                    if (product.getImgPath() != null && product.getImgName() != null && 
                        !product.getImgPath().trim().isEmpty() && !product.getImgName().trim().isEmpty()) {
                        String path = product.getImgPath().trim();
                        if (!path.endsWith("/")) path += "/";
                        imageUrl = path + product.getImgName().trim();
                    }
                    briefInfo.put("imgUrl", imageUrl);
                    
                    products.add(briefInfo);
                    
                } catch (Exception e) {
                    log.warn("제품 ID {}의 정보를 가져올 수 없습니다: {}", productId, e.getMessage());
                    // 개별 제품 오류는 무시하고 계속 진행
                }
            }
            
            return ResponseEntity.ok(products);
            
        } catch (Exception e) {
            log.error("제품 간단 정보 조회 실패", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(List.of());
        }
    }
}