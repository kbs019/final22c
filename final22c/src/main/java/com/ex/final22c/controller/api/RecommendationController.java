package com.ex.final22c.controller.api;

import com.ex.final22c.data.recommendation.SituationalRecommendation;
import com.ex.final22c.data.user.UserPreference;
import com.ex.final22c.service.chat.AsyncRecommendationService;
import com.ex.final22c.service.chat.HybridRecommendationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/recommendation")
@RequiredArgsConstructor
@Slf4j
public class RecommendationController {

    private final HybridRecommendationService hybridRecommendationService;
    private final AsyncRecommendationService asyncRecommendationService;

    /**
     * ✅ 설문 기반 AI 분석 요청 (회원/비회원 공통)
     */
    @PostMapping("/analyze")
    public ResponseEntity<UserPreference> analyze(
        @RequestParam(name = "userName", required = false) String userName,
        @RequestBody Map<String, String> survey) {
        
        log.info("추천 분석 요청: 사용자={}, 설문={}", userName, survey);
        UserPreference result = hybridRecommendationService.analyzeUserWithAI(userName, survey);
        
        // 회원인 경우 저장 여부 로그 확인
        if (userName != null && result.getPreferenceId() != null) {
            log.info("DB 저장 완료: preferenceId={}", result.getPreferenceId());
        }
        
        return ResponseEntity.ok(result);
    }



    /**
     * ✅ 비동기 백그라운드 분석 (회원용)
     */
    @PostMapping("/analyze/async")
    public ResponseEntity<?> analyzeAsync(@RequestParam("userName") String userName,
                                          @RequestBody Map<String, String> survey) {
        asyncRecommendationService.analyzeInBackground(userName, survey);
        return ResponseEntity.ok("백그라운드 분석을 시작했습니다.");
    }

    /**
     * ✅ 비동기 분석 완료 여부 확인
     */
    @GetMapping("/analyze/complete")
    public ResponseEntity<Boolean> isAnalysisComplete(@RequestParam("userName") String userName) {
        return ResponseEntity.ok(asyncRecommendationService.isAnalysisComplete(userName));
    }
}
