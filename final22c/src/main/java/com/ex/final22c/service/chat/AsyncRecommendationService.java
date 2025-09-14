package com.ex.final22c.service.chat;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.ex.final22c.data.recommendation.UserActivityData;
import com.ex.final22c.data.user.UserPreference;
import com.ex.final22c.data.user.Users;
import com.ex.final22c.repository.user.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class AsyncRecommendationService {

    private final HybridRecommendationService hybridRecommendationService;
    private final UserRepository userRepository;

    /**
     * AI 분석을 백그라운드에서 수행 (회원이면 DB 저장, 비회원이면 일회성 분석)
     */
    @Async
    public CompletableFuture<UserPreference> analyzeInBackground(String userName, Map<String, String> survey) {
        try {
            log.info("✅ 백그라운드 AI 분석 시작: {}", userName != null ? userName : "비회원");

            boolean isMember = userName != null && userRepository.findByUserName(userName).isPresent();
            UserPreference result = hybridRecommendationService.analyzeUserWithAI(userName, survey);

            log.info("✅ 백그라운드 AI 분석 완료: {}", userName != null ? userName : "비회원");

            return CompletableFuture.completedFuture(result);

        } catch (Exception e) {
            log.error("❌ 백그라운드 분석 실패: {}", userName, e);
            return CompletableFuture.completedFuture(null);
        }
    }

    /**
     * 분석 완료 여부 확인 (회원만 해당)
     */
    public boolean isAnalysisComplete(String userName) {
        if (userName == null) return false;

        try {
            UserPreference preference = hybridRecommendationService.getUserPreference(userName);
            return preference != null &&
                   preference.getAiAnalysis() != null &&
                   preference.getAiAnalysis().contains("situationalRecommendations");
        } catch (Exception e) {
            log.error("❌ 분석 완료 여부 확인 실패: {}", userName, e);
            return false;
        }
    }
}
