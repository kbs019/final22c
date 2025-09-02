package com.ex.final22c.controller.api;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import com.ex.final22c.data.recommendation.SituationalRecommendation;
import com.ex.final22c.data.recommendation.SurveySubmission;
import com.ex.final22c.service.chat.HybridRecommendationService;
import com.ex.final22c.service.chat.AsyncRecommendationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/recommendation")
@RequiredArgsConstructor
@Slf4j
public class RecommendationController {
    
    private final HybridRecommendationService recommendationService;
    private final AsyncRecommendationService asyncRecommendationService;
    
    /**
     * 설문조사 분석 요청 - 로그인 필수
     */
    @PostMapping("/analyze")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> analyzeSurvey(
            @RequestBody SurveySubmission submission,
            @AuthenticationPrincipal UserDetails principal) {
            
        // 이중 체크 (혹시 모를 경우를 대비)
        if (principal == null) {
            log.warn("비인증 사용자의 설문조사 분석 시도");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                "success", false,
                "message", "로그인이 필요합니다",
                "redirectUrl", "/user/login"
            ));
        }
        
        String userName = principal.getUsername();
        log.info("설문조사 분석 요청: {}", userName);
        
        try {
            // 이미 설문을 완료한 사용자인지 확인
            boolean alreadyCompleted = recommendationService.hasUserCompletedSurvey(userName);
            if (alreadyCompleted) {
                log.info("이미 설문을 완료한 사용자: {}", userName);
                return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "이미 분석이 완료되었습니다",
                    "isReanalysis", true
                ));
            }
            
            // 비동기로 분석 시작
            asyncRecommendationService.analyzeInBackground(userName, submission.getAnswers());
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "AI가 당신의 취향을 분석 중입니다...",
                "analysisStarted", true
            ));
            
        } catch (Exception e) {
            log.error("설문조사 분석 요청 실패: {}", userName, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "success", false,
                "message", "분석 중 오류가 발생했습니다",
                "error", e.getMessage()
            ));
        }
    }
    
    /**
     * 분석 상태 확인 - 로그인 필수
     */
    @GetMapping("/status")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> getSurveyStatus(@AuthenticationPrincipal UserDetails principal) {
        
        // 이중 체크
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                "complete", false,
                "message", "로그인이 필요합니다"
            ));
        }

        String username = principal.getUsername();
        
        try {
            boolean isCompleted = asyncRecommendationService.isAnalysisComplete(username);
            
            return ResponseEntity.ok(Map.of(
                "complete", isCompleted,
                "message", isCompleted ? "분석이 완료되었습니다" : "분석 진행 중..."
            ));
            
        } catch (Exception e) {
            log.error("분석 상태 확인 실패: {}", username, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "complete", false,
                "message", "상태 확인 중 오류가 발생했습니다"
            ));
        }
    }
    
    /**
     * 상황별 추천 조회 - 로그인 필수
     */
    @GetMapping("/situational")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> getSituationalRec(
            @RequestParam("situation") String situation,
            @AuthenticationPrincipal UserDetails principal) {
            
        // 이중 체크
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                "success", false,
                "message", "로그인이 필요합니다"
            ));
        }
        
        String userName = principal.getUsername();
        
        try {
            SituationalRecommendation result = recommendationService
                .getSituationalRecommendation(userName, situation);
                
            return ResponseEntity.ok(Map.of(
                "success", true,
                "data", result
            ));
            
        } catch (Exception e) {
            log.error("상황별 추천 조회 실패: {} - {}", userName, situation, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "success", false,
                "message", "추천 정보를 불러올 수 없습니다"
            ));
        }
    }
    
    /**
     * 모든 상황별 추천 한번에 조회 - 로그인 필수
     */
    @GetMapping("/all")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> getAllSituationalRecs(
            @AuthenticationPrincipal UserDetails principal) {
            
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                "success", false,
                "message", "로그인이 필요합니다"
            ));
        }
        
        String userName = principal.getUsername();
        
        try {
            Map<String, SituationalRecommendation> allRecs = recommendationService
                .getAllSituationalRecommendations(userName);
                
            return ResponseEntity.ok(Map.of(
                "success", true,
                "data", allRecs
            ));
            
        } catch (Exception e) {
            log.error("전체 추천 조회 실패: {}", userName, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "success", false,
                "message", "추천 정보를 불러올 수 없습니다"
            ));
        }
    }
    
    /**
     * 설문조사 재시작 (기존 데이터 삭제) - 로그인 필수
     */
    @PostMapping("/reset")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> resetSurvey(
            @AuthenticationPrincipal UserDetails principal) {
            
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                "success", false,
                "message", "로그인이 필요합니다"
            ));
        }
        
        String userName = principal.getUsername();
        
        try {
            recommendationService.resetUserSurvey(userName);
            log.info("설문조사 데이터 초기화: {}", userName);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "설문조사가 초기화되었습니다"
            ));
            
        } catch (Exception e) {
            log.error("설문조사 초기화 실패: {}", userName, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "success", false,
                "message", "초기화 중 오류가 발생했습니다"
            ));
        }
    }
}