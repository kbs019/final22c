package com.ex.final22c.service.chat;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class AsyncRecommendationService {
    
    private final HybridRecommendationService hybridRecommendationService;
    
    @Async
    public CompletableFuture<Void> analyzeInBackground(String userName, Map<String, String> survey) {
        try {
            log.info("백그라운드 AI 분석 시작: {}", userName);
            hybridRecommendationService.analyzeUserWithAI(userName, survey);
            log.info("백그라운드 AI 분석 완료: {}", userName);
        } catch (Exception e) {
            log.error("백그라운드 분석 실패: {}", userName, e);
        }
        return CompletableFuture.completedFuture(null);
    }
    
    /**
     * 분석 완료 여부 확인
     */
    public boolean isAnalysisComplete(String userName) {
        try {
            // HybridRecommendationService를 통해 사용자 선호도가 저장되어 있는지 확인
            var recommendation = hybridRecommendationService.getSituationalRecommendation(userName, "daily");
            return recommendation != null && 
                   !recommendation.getAnalysis().contains("설문조사를 먼저 완료해주세요");
        } catch (Exception e) {
            log.error("분석 완료 여부 확인 실패: {}", userName, e);
            return false;
        }
    }
}