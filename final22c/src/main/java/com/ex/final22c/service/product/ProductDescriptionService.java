package com.ex.final22c.service.product;

import com.ex.final22c.data.product.Product;
import com.ex.final22c.service.chat.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductDescriptionService {
    
    private final ChatService chatService;
    
    // 메모리 캐시 - productId를 키로 사용
    private final Map<Long, CachedDescription> descriptionCache = new ConcurrentHashMap<>();
    
    // 캐시 유효 시간 (24시간)
    private static final long CACHE_DURATION_HOURS = 24;
    
    public String generateEnhancedDescription(Product product) {
        // 1. 캐시 확인
        CachedDescription cached = descriptionCache.get(product.getId());
        if (cached != null && !cached.isExpired()) {
            log.debug("캐시된 설명문 사용: productId={}", product.getId());
            return cached.getDescription();
        }
        
        // 2. 캐시가 없거나 만료된 경우 새로 생성
        log.debug("새로운 설명문 생성 시작: productId={}", product.getId());
        
        // 향 구조 분석
        boolean hasSingleNote = !isEmpty(product.getSingleNote());
        boolean hasComplexNotes = !isEmpty(product.getTopNote()) || 
                                 !isEmpty(product.getMiddleNote()) || 
                                 !isEmpty(product.getBaseNote());
        
        String prompt = buildPromptBasedOnNoteStructure(product, hasSingleNote, hasComplexNotes);
        
        String description;
        try {
            String aiResult = chatService.generateProductDescription(prompt);
            description = formatAiDescription(aiResult, hasSingleNote, hasComplexNotes);
            log.debug("AI 설명문 생성 완료: productId={}", product.getId());
        } catch (Exception e) {
            log.error("AI 설명문 생성 실패: {}", e.getMessage(), e);
            description = generateFallbackDescription(product, hasSingleNote, hasComplexNotes);
        }
        
        // 3. 생성된 설명문을 캐시에 저장
        if (description != null) {
            descriptionCache.put(product.getId(), new CachedDescription(description));
            log.debug("설명문 캐시 저장 완료: productId={}", product.getId());
        }
        
        return description;
    }
    
    private String buildPromptBasedOnNoteStructure(Product product, boolean hasSingle, boolean hasComplex) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("다음 향수 정보를 바탕으로 매력적인 상품 가이드를 작성해주세요:\n\n");
        
        // 기본 정보
        prompt.append("브랜드: ").append(product.getBrand().getBrandName()).append("\n");
        prompt.append("제품명: ").append(product.getName()).append("\n");
        prompt.append("부향률: ").append(product.getGrade().getGradeName()).append("\n");
        prompt.append("메인어코드: ").append(product.getMainNote().getMainNoteName()).append("\n");
        
        if (hasSingle) {
            // 싱글노트 향수인 경우
            prompt.append("싱글노트: ").append(product.getSingleNote()).append("\n\n");
            prompt.append("이 향수는 싱글노트 향수입니다. 다음 조건으로 작성:\n");
            prompt.append("- 단일 향의 순수함과 집중도 강조\n");
            prompt.append("- 해당 노트의 특성과 매력 설명\n");
            prompt.append("- 싱글노트만의 장점 (깔끔함, 레이어링 용이성 등) 언급\n");
            prompt.append("- 어떤 상황에서 사용하면 좋은지 구체적으로 제안\n");
            
        } else if (hasComplex) {
            // 복합 노트 향수인 경우
            if (!isEmpty(product.getTopNote())) {
                prompt.append("탑노트: ").append(product.getTopNote()).append("\n");
            }
            if (!isEmpty(product.getMiddleNote())) {
                prompt.append("미들노트: ").append(product.getMiddleNote()).append("\n");
            }
            if (!isEmpty(product.getBaseNote())) {
                prompt.append("베이스노트: ").append(product.getBaseNote()).append("\n");
            }
            prompt.append("\n이 향수는 복합 구조를 가집니다. 다음 조건으로 작성:\n");
            prompt.append("- 향의 변화 과정을 시간순으로 스토리텔링\n");
            prompt.append("- 각 노트가 어떻게 조화를 이루는지 설명\n");
            prompt.append("- 착용 시간대별 향의 느낌 변화 서술\n");
            prompt.append("- 첫인상부터 마무리까지의 향 여정 묘사\n");
        }
        
        // 기존 설명 활용
        if (!isEmpty(product.getDescription())) {
            prompt.append("\n기존 설명: ").append(product.getDescription()).append("\n");
            prompt.append("위 설명을 참고하여 더 풍부하게 해석하세요.\n");
        }
        
        prompt.append("\n공통 요구사항:\n");
        prompt.append("- 2-3개 문단으로 구성 (150-200자 내외)\n");
        prompt.append("- 어떤 상황/사람에게 어울리는지 포함\n");
        prompt.append("- 감성적이고 전문적인 톤앤매너\n");
        prompt.append("- 구매 욕구를 자극하는 표현 사용\n");
        prompt.append("- HTML 태그 사용 금지, 순수 텍스트만\n");
        
        return prompt.toString();
    }
    
    private String formatAiDescription(String aiResult, boolean hasSingle, boolean hasComplex) {
        if (isEmpty(aiResult)) {
            return null;
        }
        
        StringBuilder formatted = new StringBuilder();
        
        // AI 생성 결과 포맷팅
        String[] paragraphs = aiResult.split("\n\n");
        for (int i = 0; i < paragraphs.length; i++) {
            String paragraph = paragraphs[i].trim();
            if (!isEmpty(paragraph)) {
                formatted.append("<p>").append(paragraph).append("</p>");
                if (i < paragraphs.length - 1) {
                    formatted.append("\n");
                }
            }
        }
        
        // 추가 가이드 섹션
        formatted.append("\n<div class='mt-3'>");
        
        if (hasSingle) {
            formatted.append("<small class='text-muted'>")
                    .append("<strong>싱글노트 tip:</strong> 다른 향수와 레이어링하기 좋은 베이스로 활용 가능")
                    .append("</small>");
        } else if (hasComplex) {
            formatted.append("<small class='text-muted'>")
                    .append("<strong>향의 변화:</strong> 시간이 지날수록 다른 매력을 발견할 수 있는 복합적인 향")
                    .append("</small>");
        }
        
        formatted.append("</div>");
        
        return formatted.toString();
    }
    
    private String generateFallbackDescription(Product product, boolean hasSingle, boolean hasComplex) {
        StringBuilder fallback = new StringBuilder();
        
        fallback.append("<p>");
        fallback.append(product.getBrand().getBrandName()).append("의 ");
        
        if (hasSingle) {
            fallback.append("싱글노트 향수로, ").append(product.getSingleNote())
                    .append("의 순수하고 깔끔한 매력을 온전히 담았습니다. ");
            fallback.append("복잡하지 않은 단일 향으로 일상에서 부담 없이 즐길 수 있으며, ");
            fallback.append("레이어링의 베이스로도 완벽합니다.");
        } else if (hasComplex) {
            fallback.append("복합적인 향 구조로 시간에 따라 다양한 매력을 선사합니다. ");
            if (!isEmpty(product.getTopNote())) {
                fallback.append("첫인상은 ").append(product.getTopNote()).append("로 시작하여 ");
            }
            if (!isEmpty(product.getBaseNote())) {
                fallback.append(product.getBaseNote()).append("로 깊이 있게 마무리됩니다.");
            }
        }
        
        fallback.append("</p>");
        
        // 부향률 정보 추가
        fallback.append("<p>");
        fallback.append(product.getGrade().getGradeName()).append(" 농도로 제조되어 ");
        fallback.append("적절한 지속력과 확산력을 자랑하며, ");
        fallback.append(product.getMainNote().getMainNoteName()).append(" 계열의 특성을 잘 살렸습니다.");
        fallback.append("</p>");
        
        return fallback.toString();
    }
    
    private boolean isEmpty(String str) {
        return str == null || str.trim().isEmpty();
    }
    
    // 캐시된 설명문을 저장하는 내부 클래스
    private static class CachedDescription {
        private final String description;
        private final LocalDateTime createdAt;
        
        public CachedDescription(String description) {
            this.description = description;
            this.createdAt = LocalDateTime.now();
        }
        
        public String getDescription() {
            return description;
        }
        
        public boolean isExpired() {
            return LocalDateTime.now().isAfter(createdAt.plusHours(CACHE_DURATION_HOURS));
        }
    }
}