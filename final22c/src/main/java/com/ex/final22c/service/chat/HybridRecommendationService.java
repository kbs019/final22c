package com.ex.final22c.service.chat;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ex.final22c.data.product.Product;
import com.ex.final22c.data.recommendation.RecommendedProduct;
import com.ex.final22c.data.recommendation.SituationalRecommendation;
import com.ex.final22c.data.user.UserPreference;
import com.ex.final22c.data.user.Users;
import com.ex.final22c.repository.productMapper.ProductMapper;
import com.ex.final22c.repository.user.UserPreferenceRepository;
import com.ex.final22c.repository.user.UserRepository;
import com.ex.final22c.service.product.ProductService;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class HybridRecommendationService {

    private final ChatService chatService;
    private final ProductMapper productMapper;
    private final ProductService productService;
    private final ObjectMapper objectMapper;
    private final UserPreferenceRepository userPreferenceRepository;
    private final UserRepository userRepository;

    /** 사용자명으로 선호도 1건 조회 */
    public UserPreference getUserPreference(String userName) {
        return userPreferenceRepository.findByUser_UserName(userName).orElse(null);
    }

    /** 비회원(게스트) 분석: DB 저장 없이 ai JSON만 반환 */
    @Transactional(readOnly = true)
    public String analyzeForGuest(Map<String, String> surveyAnswers) {
        final Map<String, String> safe =
            (surveyAnswers == null) ? Collections.emptyMap() : surveyAnswers;

        List<Map<String, Object>> candidates = getFilteredCandidates(safe);

        String aiJson;
        try {
            aiJson = callAiAnalysis(safe, candidates);
        } catch (Exception e) {
            log.warn("게스트 AI 호출 실패. fallback JSON으로 대체합니다.", e);
            aiJson = null;
        }
        if (aiJson == null || aiJson.isBlank()) {
            aiJson = buildFallbackJson(safe, candidates);
        } else {
            aiJson = normalizeJson(aiJson);
            if (!isValidJson(aiJson)) {
                log.warn("게스트 AI JSON invalid. fallback JSON 사용");
                aiJson = buildFallbackJson(safe, candidates);
            }
        }
        return aiJson;
    }

    /** 회원 분석: 유저 1명당 1행 upsert(있으면 update, 없으면 insert) */
    @Transactional
    public UserPreference analyzeUserWithAI(String userName, Map<String, String> surveyAnswers) {
        // 1) 후보 선별
        final Map<String, String> answersSafe =
            (surveyAnswers == null) ? Collections.emptyMap() : surveyAnswers;
        List<Map<String, Object>> candidates = getFilteredCandidates(answersSafe);

        // 2) AI 호출 (실패/빈값이면 fallback JSON)
        String aiResult;
        try {
            aiResult = callAiAnalysis(answersSafe, candidates);
        } catch (Exception e) {
            log.warn("AI 호출 실패. fallback JSON으로 대체합니다.", e);
            aiResult = null;
        }
        if (aiResult == null || aiResult.isBlank()) {
            aiResult = buildFallbackJson(answersSafe, candidates);
        } else {
            aiResult = normalizeJson(aiResult);
            if (!isValidJson(aiResult)) {
                log.warn("AI JSON invalid. fallback JSON으로 대체");
                aiResult = buildFallbackJson(answersSafe, candidates);
            }
        }

        // 3) 회원 조회 (필수)
        Users user = userRepository.findByUserName(userName)
            .orElseThrow(() -> new RuntimeException("회원 정보가 없습니다: " + userName));

        // 4) Upsert (유저 1명당 1행)
        UserPreference pref = userPreferenceRepository.findByUser_UserNo(user.getUserNo())
            .orElseGet(() -> UserPreference.builder()
                .user(user)          // ★ userNo NOT NULL/UNIQUE 대응
                .userName(userName)  // 부가정보
                .build());

        // 5) 필드 갱신
        try {
            pref.setSurveyAnswers(objectMapper.writeValueAsString(answersSafe));
        } catch (Exception e) {
            log.warn("설문 JSON 직렬화 실패, 빈 객체로 저장합니다.", e);
            pref.setSurveyAnswers("{}");
        }
        pref.setAiAnalysis(aiResult);
        pref.setRecommendedProducts(extractProductIds(aiResult)); // "1,2,3"

        // 6) 저장 (있으면 UPDATE, 없으면 INSERT)
        return userPreferenceRepository.save(pref);
    }

    /** 회원 결과 리셋(행 삭제) */
    @Transactional
    public void resetByUserName(String userName) {
        try {
            userPreferenceRepository.deleteByUser_UserName(userName);
        } catch (Exception e) {
            log.warn("resetByUserName 실패 userName={}", userName, e);
        }
    }

    // ===================== 내부 유틸 =====================

    /** 설문 조건을 기반으로 후보 상품 선별 */
    private List<Map<String, Object>> getFilteredCandidates(Map<String, String> survey) {
        String gender = survey.get("gender");
        String priceRange = survey.get("priceRange");
        Long mainNoteId = convertNoteToId(survey.get("notes"));
        List<Long> mainNoteIds = (mainNoteId != null) ? List.of(mainNoteId) : null;
        List<Long> gradeIds = getGradeIdsByIntensity(survey.get("intensity"));

        return productMapper.selectProductsForRecommendation(
            null, gradeIds, mainNoteIds, null, null, gender, priceRange
        );
    }

    /** AI 프롬프트 구성 및 호출 (응답은 JSON 문자열, situationalRecommendations 고정) */
    private String callAiAnalysis(Map<String, String> survey, List<Map<String, Object>> candidates) {
        String usage = survey.getOrDefault("usage", "daily");

        StringBuilder prompt = new StringBuilder();
        prompt.append("당신은 전자상거래 향수 추천 시스템입니다.\n")
              .append("반드시 'JSON 한 줄'만 출력하세요. 설명/머릿말/코드펜스/마크다운 금지.\n")
              .append("productId는 반드시 아래 후보의 ID에서만 선택하고, 숫자 타입으로 출력하세요.\n\n");

        // 설문 결과 요약
        prompt.append("=== 사용자 설문 ===\n");
        survey.forEach((k, v) -> prompt.append(k).append(": ").append(v).append("\n"));

        // 후보 상품 목록
        prompt.append("\n=== 후보 상품 ===\n");
        candidates.forEach(p -> prompt.append("id=").append(p.get("id"))
            .append(", name=").append(p.get("name"))
            .append(", brand=").append(p.get("brandName"))
            .append(", volume=").append(p.get("volume"))
            .append(", price=").append(p.get("sellPrice"))
            .append("\n"));

        // 요구사항 + 스키마 고정
        prompt.append("\n=== 요구사항 ===\n")
              .append("- 사용 용도는 \"").append(usage).append("\" 입니다.\n")
              .append("- 용도에 맞는 향수 2~3개 추천 + 간단한 이유.\n")
              .append("- 출력 스키마(키 추가 금지):\n")
              .append("{\"situationalRecommendations\":{")
              .append("\"").append(usage).append("\":{")
              .append("\"reason\":\"string\",")
              .append("\"products\":[{\"productId\":111,\"reason\":\"string\"}]")
              .append("}}}\n")
              .append("- JSON 이외 텍스트 금지, code fence 금지, 줄바꿈 최소화.\n");

        String raw = chatService.ask(prompt.toString());
        return raw;
    }

    /** 코드펜스/프리엠블 제거 등 JSON 정규화 */
    private String normalizeJson(String raw) {
        if (raw == null) return "";
        String s = raw.trim();
        // ```json ... ``` 제거
        s = s.replaceAll("^```(json)?\\s*", "").replaceAll("\\s*```\\s*$", "");
        // JSON 시작 전 잡음 제거 (예: zzimers 같은 쓰레기 토큰)
        int firstBrace = s.indexOf('{');
        if (firstBrace > 0) s = s.substring(firstBrace);
        // 마지막 닫는 괄호 뒤 꼬리 제거
        int lastBrace = s.lastIndexOf('}');
        if (lastBrace >= 0 && lastBrace < s.length() - 1) {
            s = s.substring(0, lastBrace + 1);
        }
        return s;
    }

    /** JSON 유효성 검사 */
    private boolean isValidJson(String s) {
        try { objectMapper.readTree(s); return true; } catch (Exception e) { return false; }
    }

    /** AI JSON에서 productId 목록을 "1,2,3" 형태로 추출 (situationalRecommendations 가정) */
    private String extractProductIds(String aiResult) {
        try {
            Map<String, Object> analysis = objectMapper.readValue(aiResult, Map.class);
            Map<String, Object> situationalRecs =
                (Map<String, Object>) analysis.get("situationalRecommendations");
            if (situationalRecs == null) return "";

            Set<Long> ids = new HashSet<>();
            for (Object val : situationalRecs.values()) {
                if (!(val instanceof Map)) continue;
                Map<String, Object> sit = (Map<String, Object>) val;
                List<Map<String, Object>> prods =
                    (List<Map<String, Object>>) sit.get("products");
                if (prods != null) {
                    prods.forEach(p -> {
                        Object obj = p.get("productId");
                        if (obj instanceof Number) {
                            ids.add(((Number) obj).longValue());
                        } else if (obj != null) {
                            try {
                                ids.add(Long.parseLong(obj.toString()));
                            } catch (Exception ignored) {}
                        }
                    });
                }
            }
            return ids.stream().map(String::valueOf).collect(Collectors.joining(","));
        } catch (Exception e) {
            log.error("상품 ID 추출 실패", e);
            return "";
        }
    }

    /** 프론트 표시에 사용할 파싱(상황별) — 필요 시 사용 */
    public SituationalRecommendation getParsedRecommendation(String aiJson, String situation) {
        try {
            String cleaned = normalizeJson(aiJson);
            Map<String, Object> analysis = objectMapper.readValue(cleaned, Map.class);
            Map<String, Object> recs =
                (Map<String, Object>) analysis.get("situationalRecommendations");
            if (recs == null || !recs.containsKey(situation)) return getEmptyRecommendation(situation);

            Map<String, Object> sitData = (Map<String, Object>) recs.get(situation);
            List<Map<String, Object>> prodData =
                (List<Map<String, Object>>) sitData.get("products");
            var products = prodData.stream()
                .map(this::mapToRecommendedProduct)
                .filter(p -> p != null)
                .collect(Collectors.toList());

            return SituationalRecommendation.builder()
                .situation(situation)
                .products(products)
                .analysis((String) sitData.get("reason"))
                .build();

        } catch (Exception e) {
            log.error("추천 결과 파싱 실패", e);
            return getEmptyRecommendation(situation);
        }
    }

    private RecommendedProduct mapToRecommendedProduct(Map<String, Object> pd) {
        try {
            Object obj = pd.get("productId");
            long id = (obj instanceof Number)
                ? ((Number) obj).longValue()
                : Long.parseLong(obj.toString());

            Product p = productService.getProduct(id);
            if (p == null) return null;

            String img = "/img/noimg.png";
            if (p.getImgPath() != null && p.getImgName() != null) {
                String path = p.getImgPath();
                if (!path.endsWith("/")) path += "/";
                img = path + p.getImgName();
            }

            return RecommendedProduct.builder()
                .productId(id)
                .name(p.getName())
                .brandName(p.getBrand() != null ? p.getBrand().getBrandName() : "")
                .volume((String) pd.get("volume"))
                .price(p.getSellPrice())
                .reason((String) pd.get("reason"))
                .imageUrl(img)
                .build();

        } catch (Exception e) {
            log.error("상품 매핑 실패", e);
            return null;
        }
    }

    private SituationalRecommendation getEmptyRecommendation(String situation) {
        return SituationalRecommendation.builder()
            .situation(situation)
            .products(Collections.emptyList())
            .analysis("설문조사를 먼저 완료해주세요")
            .build();
    }

    private Long convertNoteToId(String note) {
        if (note == null) return 6L;
        Map<String, Long> m = Map.of(
            "floral", 6L, "citrus", 7L, "woody", 2L,
            "spicy", 1L, "vanilla", 8L, "fruity", 4L,
            "herbal", 3L, "gourmand", 5L
        );
        return m.getOrDefault(note.toLowerCase(), 6L);
    }

    private List<Long> getGradeIdsByIntensity(String intensity) {
        if (intensity == null) return List.of(3L, 4L);
        Map<String, List<Long>> m = Map.of(
            "light", List.of(2L, 4L),
            "medium", List.of(3L, 4L),
            "strong", List.of(1L, 3L)
        );
        // 너가 설문에서 "오드 뚜왈렛" 같은 한글을 쓰는 경우 여기서 변환 후 매핑해도 됨
        return m.getOrDefault(intensity.toLowerCase(), List.of(3L, 4L));
    }

    // ===== Fallback(JSON 문자열, situationalRecommendations 포맷) =====
    private String buildFallbackJson(Map<String, String> survey, List<Map<String, Object>> candidates) {
        try {
            if (candidates == null || candidates.isEmpty()) {
                return "{\"situationalRecommendations\":{}}";
            }
            // 2~3개 랜덤 픽
            Collections.shuffle(candidates);
            List<Map<String, Object>> picks = candidates.stream().limit(3).collect(Collectors.toList());

            String usage = survey.getOrDefault("usage", "daily");
            List<Map<String, Object>> prods = picks.stream().map(p -> Map.of(
                "productId", p.get("id"),
                "reason", "설문 조건과 유사한 후보에서 임시 추천"
            )).collect(Collectors.toList());

            Map<String, Object> usageRec = new HashMap<>();
            usageRec.put("reason", "AI 분석 실패 시 임시 추천 결과");
            usageRec.put("products", prods);

            Map<String, Object> root = Map.of("situationalRecommendations", Map.of(usage, usageRec));
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            log.error("Fallback JSON 생성 실패", e);
            return "{\"situationalRecommendations\":{}}";
        }
    }
}
