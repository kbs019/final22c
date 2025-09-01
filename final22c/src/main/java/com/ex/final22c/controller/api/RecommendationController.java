package com.ex.final22c.controller.api;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import com.ex.final22c.data.recommendation.SituationalRecommendation;
import com.ex.final22c.data.recommendation.SurveySubmission;
import com.ex.final22c.data.user.UserPreference;
import com.ex.final22c.service.chat.HybridRecommendationService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/recommendation")
@RequiredArgsConstructor
public class RecommendationController {
    
    private final HybridRecommendationService recommendationService;
    
    @PostMapping("/analyze")
    public ResponseEntity<Map<String, Object>> analyzeSurvey(
            @RequestBody SurveySubmission submission,
            @AuthenticationPrincipal UserDetails principal) {
            
        if (principal == null) {
            return ResponseEntity.ok(Map.of(
                "success", false,
                "message", "로그인이 필요합니다"
            ));
        }
        
        String userName = principal.getUsername();
        
        try {
            UserPreference preference = recommendationService
                .analyzeUserWithAI(userName, submission.getAnswers());
                
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "취향 분석이 완료되었습니다!",
                "analysisId", preference.getPreferenceId()
            ));
            
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of(
                "success", false,
                "message", "분석 중 오류가 발생했습니다",
                "error", e.getMessage()
            ));
        }
    }
    
    @GetMapping("/situational")
    public ResponseEntity<SituationalRecommendation> getSituationalRec(
            @RequestParam("situation") String situation,
            @AuthenticationPrincipal UserDetails principal) {
            
        if (principal == null) {
            return ResponseEntity.badRequest().build();
        }
        
        String userName = principal.getUsername();
        SituationalRecommendation result = recommendationService
            .getSituationalRecommendation(userName, situation);
            
        return ResponseEntity.ok(result);
    }
    
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getSurveyStatus(@AuthenticationPrincipal UserDetails principal) {
        if (principal == null) {
            return ResponseEntity.status(401).body(Map.of("complete", false));
        }

        String username = principal.getUsername();
        boolean isCompleted = recommendationService.hasUserCompletedSurvey(username); 

        return ResponseEntity.ok(Map.of("complete", isCompleted));
    }
    
}