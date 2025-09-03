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

    // ===================== 내부 유틸 =====================
 // ===================== 내부 유틸 =====================
    /** 설문 기반 후보 선별 (성별/강도/메인노트/가격은 DB에서 필터) + 후보 상한 */
    private List<Map<String, Object>> getFilteredCandidates(Map<String, String> survey) {
        final String gender     = survey.get("gender");       // male/female/null
        final String priceRange = survey.get("priceRange");   // low/medium/high

        final Integer mainNoteId    = convertNoteToId(survey.get("notes"));
        final List<Integer> mainNoteIds = (mainNoteId != null) ? List.of(mainNoteId) : null;

        final List<Integer> gradeIds = getGradeIdsByIntensity(survey.get("intensity"));

        List<Map<String, Object>> candidates =
            productMapper.selectProductsForRecommendation(
                null,          // brandIds
                gradeIds,
                mainNoteIds,
                null,          // volumeIds
                null,          // keyword
                gender,        // 정렬 가중치용 (gs 서브쿼리)
                priceRange     // 가격대 필터
            )
            .stream()
            // SQL에서 NVL(p.COUNT,0) > 0로 걸렀지만 혹시 모를 null 방어
            .filter(p -> {
                Object v = p.get("count");
                if (v instanceof Number) return ((Number) v).intValue() > 0;
                if (v != null) try { return Integer.parseInt(String.valueOf(v)) > 0; } catch (Exception ignore) {}
                return true;
            })
            .limit(30)  // 프롬프트 안정화
            .collect(Collectors.toList());

        log.debug("filteredCandidates gender={}, priceRange={}, gradeIds={}, mainNoteIds={}, size={}",
            gender, priceRange, gradeIds, mainNoteIds, candidates.size());

        return candidates;
    }
    
    /** 비회원(게스트) 분석: DB 저장 없이 ai JSON만 반환 */
    @Transactional(readOnly = true)
    public String analyzeForGuest(Map<String, String> surveyAnswers) {
        final Map<String, String> safe =
            (surveyAnswers == null) ? Collections.emptyMap() : surveyAnswers;

        List<Map<String, Object>> candidates = getFilteredCandidates(safe);

        String aiJson;
        boolean usedFallback = false; // ★ 진단
        try {
            aiJson = callAiAnalysis(safe, candidates);
        } catch (Exception e) {
            log.warn("게스트 AI 호출 실패. fallback JSON으로 대체합니다.", e);
            aiJson = null;
        }
        if (aiJson == null || aiJson.isBlank()) {
            usedFallback = true;
            aiJson = buildFallbackJson(safe, candidates);
        } else {
            aiJson = normalizeJson(aiJson);
            if (!isValidJson(aiJson)) {
                log.warn("게스트 AI JSON invalid. fallback JSON 사용");
                usedFallback = true;
                aiJson = buildFallbackJson(safe, candidates);
            }
        }

        log.info("analyzeForGuest usedFallback={}, candidates={}", usedFallback, candidates.size());
        return aiJson;
    }

    /** 회원 분석: 유저 1명당 1행 upsert(있으면 update, 없으면 insert) */
    @Transactional
    public UserPreference analyzeUserWithAI(String userName, Map<String, String> surveyAnswers) {
        final Map<String, String> answersSafe =
            (surveyAnswers == null) ? Collections.emptyMap() : surveyAnswers;

        // 1) 후보 선별
        List<Map<String, Object>> candidates = getFilteredCandidates(answersSafe);

        // 2) AI 호출 (실패/빈값이면 fallback JSON)
        String aiResult;
        boolean usedFallback = false; // ★ 진단
        try {
            aiResult = callAiAnalysis(answersSafe, candidates);
        } catch (Exception e) {
            log.warn("AI 호출 실패. fallback JSON으로 대체합니다.", e);
            aiResult = null;
        }
        if (aiResult == null || aiResult.isBlank()) {
            usedFallback = true;
            aiResult = buildFallbackJson(answersSafe, candidates);
        } else {
            aiResult = normalizeJson(aiResult);
            if (!isValidJson(aiResult)) {
                log.warn("AI JSON invalid. fallback JSON으로 대체");
                usedFallback = true;
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

        // 6) 저장
        UserPreference saved = userPreferenceRepository.save(pref);

        log.info("AI 분석 저장: user={}, usedFallback={}, candidateSize={}, ids={}",
            userName, usedFallback, candidates.size(), saved.getRecommendedProducts());

        return saved;
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

    /**
     * 개선된 AI 프롬프트 구성 및 호출 (응답은 JSON 문자열, situationalRecommendations 고정)
     */
    private String callAiAnalysis(Map<String, String> survey, List<Map<String, Object>> candidates) {
        String usage = survey.getOrDefault("usage", "daily");

        StringBuilder prompt = new StringBuilder();
        prompt.append("당신은 전자상거래 향수 추천 시스템입니다.\n")
              .append("반드시 완전한 JSON 형태로만 응답하세요. 설명문, 코드펜스, 마크다운 금지.\n")
              .append("productId는 반드시 아래 후보의 ID에서만 선택하고, 숫자 타입으로 출력하세요.\n\n");

        // 설문 결과 요약
        prompt.append("=== 사용자 설문 ===\n");
        survey.forEach((k, v) -> prompt.append(k).append(": ").append(v).append("\n"));

        // 후보 상품 목록 (상한은 getFilteredCandidates에서 이미 적용됨)
        prompt.append("\n=== 후보 상품 ===\n");
        candidates.forEach(p -> prompt.append("id=").append(p.get("id"))
            .append(", name=").append(p.get("name"))
            .append(", brand=").append(p.get("brandName"))
            .append(", volume=").append(p.get("volume"))
            .append(", price=").append(p.get("sellPrice"))
            .append("\n"));

        // 요구사항 + 스키마 고정
        prompt.append("\n=== 필수 출력 형식 ===\n")
              .append("다음 JSON 구조로만 응답하세요:\n")
              .append("{\"situationalRecommendations\":{\"").append(usage).append("\":{")
              .append("\"reason\":\"추천 이유 설명\",")
              .append("\"products\":[")
              .append("{\"productId\":숫자,\"reason\":\"개별 추천 이유\"},")
              .append("{\"productId\":숫자,\"reason\":\"개별 추천 이유\"}")
              .append("]}}}\n")
              .append("\n중요: JSON 이외의 텍스트, 설명, 코드펜스 절대 금지!\n");

        try {
            String raw = chatService.ask(prompt.toString());
            log.debug("AI 원본 응답: {}", raw);

            String normalized = normalizeJson(raw);
            log.debug("정규화된 응답: {}", normalized);

            // 정규화된 결과가 유효하지 않으면 폴백 파서 시도
            if (!isValidJson(normalized)) {
                log.warn("정규화된 JSON이 유효하지 않음, 텍스트 추출 시도");
                normalized = extractSituationalRecommendationsFromText(raw, usage);
            }
            return normalized;

        } catch (Exception e) {
            log.error("AI 호출 또는 후처리 실패", e);
            throw e; // 상위에서 폴백 처리
        }
    }

    /** JSON 정규화 (과도한 절단 방지) */
    private String normalizeJson(String raw) {
        if (raw == null || raw.trim().isEmpty()) return "{}";
        String s = raw.trim();
        log.debug("정규화 전: {}", s);

        // 코드펜스 제거
        if (s.startsWith("```")) {
            s = s.replaceFirst("^```(?:json)?\\s*", "")
                 .replaceFirst("\\s*```\\s*$", "").trim();
        }

        // 이미 완전한 JSON처럼 보이면 최소 정리만
        if (s.startsWith("{") && s.endsWith("}")) {
            return s.replaceAll(",\\s*([}\\]])", "$1");
        }

        // JSON 시작 전 잡음 제거
        int firstBrace = s.indexOf('{');
        if (firstBrace == -1) {
            log.warn("JSON에서 시작 중괄호를 찾을 수 없음");
            return "{}";
        }
        s = s.substring(firstBrace);

        // 중괄호 균형 맞추기
        int depth = 0;
        int endIndex = s.length() - 1;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '{') depth++;
            else if (ch == '}') {
                depth--;
                if (depth == 0) { endIndex = i; break; }
            }
        }

        if (endIndex < s.length() - 1) {
            s = s.substring(0, endIndex + 1);
        }

        // 트레일링 콤마 제거
        s = s.replaceAll(",\\s*([}\\]])", "$1");
        log.debug("정규화 후: {}", s);
        return s;
    }

    /** JSON 유효성 검사 (situationalRecommendations 우선 확인) */
    private boolean isValidJson(String s) {
        if (s == null || s.trim().isEmpty()) return false;
        try {
            Map<String, Object> parsed = objectMapper.readValue(s, Map.class);
            if (parsed.containsKey("situationalRecommendations")) {
                Object recs = parsed.get("situationalRecommendations");
                if (recs instanceof Map) {
                    return !((Map<?, ?>) recs).isEmpty();
                }
            }
            return true;
        } catch (Exception e) {
            log.debug("JSON 유효성 검사 실패: {}", e.getMessage());
            return false;
        }
    }

    /** situationalRecommendations가 없으면 텍스트에서 추출 */
    private String extractSituationalRecommendationsFromText(String rawText, String targetUsage) {
        try {
            int startIndex = rawText.indexOf("\"situationalRecommendations\"");
            if (startIndex == -1) {
                return createSimpleRecommendationJson(rawText, targetUsage);
            }
            int braceStart = rawText.indexOf('{', startIndex);
            if (braceStart == -1) return createSimpleRecommendationJson(rawText, targetUsage);

            int depth = 0;
            int endIndex = braceStart;
            for (int i = braceStart; i < rawText.length(); i++) {
                char ch = rawText.charAt(i);
                if (ch == '{') depth++;
                else if (ch == '}') {
                    depth--;
                    if (depth == 0) { endIndex = i; break; }
                }
            }
            String extracted = "{\"situationalRecommendations\":" +
                               rawText.substring(braceStart, endIndex + 1) + "}";
            extracted = extracted.replaceAll(",\\s*([}\\]])", "$1");
            log.debug("추출된 JSON: {}", extracted);
            return extracted;

        } catch (Exception e) {
            log.warn("텍스트에서 JSON 추출 실패: {}", e.getMessage());
            return createSimpleRecommendationJson(rawText, targetUsage);
        }
    }

    /** 간단한 추천 JSON 생성 (최후 폴백) */
    private String createSimpleRecommendationJson(String rawText, String usage) {
        try {
            var pattern = java.util.regex.Pattern.compile("\"productId\"\\s*:\\s*(\\d+)");
            var matcher = pattern.matcher(rawText);

            List<Map<String, Object>> products = new java.util.ArrayList<>();
            while (matcher.find() && products.size() < 3) {
                long productId = Long.parseLong(matcher.group(1));
                products.add(Map.of("productId", productId, "reason", "AI 분석 결과에서 추출된 추천"));
            }
            if (products.isEmpty()) {
                products = List.of(
                    Map.of("productId", 1, "reason", "기본 추천 상품"),
                    Map.of("productId", 2, "reason", "기본 추천 상품")
                );
            }

            Map<String, Object> situationData = new HashMap<>();
            situationData.put("reason", "AI 분석 결과를 기반한 추천");
            situationData.put("products", products);
            situationData.put("source", "fallback"); // ★ 진단

            Map<String, Object> situationalRecs = new HashMap<>();
            situationalRecs.put(usage != null ? usage : "daily", situationData);

            Map<String, Object> root = new HashMap<>();
            root.put("situationalRecommendations", situationalRecs);

            return objectMapper.writeValueAsString(root);

        } catch (Exception e) {
            log.error("간단한 추천 JSON 생성 실패", e);
            return "{\"situationalRecommendations\":{\"daily\":{\"reason\":\"추천 결과를 불러올 수 없습니다\",\"products\":[],\"source\":\"fallback\"}}}";
        }
    }

    /** 개선된 AI JSON에서 productId 목록을 "1,2,3" 형태로 추출 */
    private String extractProductIds(String aiResult) {
        if (aiResult == null || aiResult.trim().isEmpty()) return "";
        try {
            String normalized = normalizeJson(aiResult);
            Map<String, Object> analysis = objectMapper.readValue(normalized, Map.class);
            Map<String, Object> situationalRecs =
                (Map<String, Object>) analysis.get("situationalRecommendations");
            if (situationalRecs == null) {
                log.warn("situationalRecommendations이 null입니다");
                return "";
            }
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
                            try { ids.add(Long.parseLong(obj.toString())); }
                            catch (NumberFormatException e) { log.warn("productId 파싱 실패: {}", obj); }
                        }
                    });
                }
            }
            return ids.stream().map(String::valueOf).collect(Collectors.joining(","));
        } catch (Exception e) {
            log.error("상품 ID 추출 실패", e);
            return extractProductIdsFromText(aiResult);
        }
    }

    /** 텍스트에서 직접 productId 추출 (폴백) */
    private String extractProductIdsFromText(String text) {
        try {
            var pattern = java.util.regex.Pattern.compile("\"productId\"\\s*:\\s*(\\d+)");
            var matcher = pattern.matcher(text);
            Set<String> ids = new java.util.LinkedHashSet<>();
            while (matcher.find()) ids.add(matcher.group(1));
            return String.join(",", ids);
        } catch (Exception e) {
            log.error("텍스트에서 productId 추출 실패", e);
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
    private Integer convertNoteToId(String note) {
        if (note == null) return 6;
        Map<String, Integer> m = new HashMap<>();
        m.put("floral", 6);   m.put("플로럴", 6);
        m.put("citrus", 7);   m.put("시트러스", 7);
        m.put("woody",  2);   m.put("우디", 2);
        m.put("spicy",  1);   m.put("스파이시", 1);
        m.put("fruity", 4);   m.put("프루티", 4);
        m.put("herbal", 3);   m.put("허벌", 3);
        return m.getOrDefault(note.toLowerCase(), 6);
    }


    private List<Integer> getGradeIdsByIntensity(String intensity) {
        if (intensity == null || intensity.isBlank()) return List.of(3, 4);
        String k = intensity.trim().toLowerCase();

        switch (k) {
            case "오드 코롱": case "eau de cologne": case "light":
                return List.of(2);
            case "오드 뚜왈렛": case "eau de toilette": case "medium":
                return List.of(4);
            case "오드 퍼퓸": case "eau de parfum": case "strong":
                return List.of(3);
            case "퍼퓸": case "parfum":
                return List.of(1);
            default:
                return List.of(3, 4);
        }
    }

    // ===== Fallback(JSON 문자열, situationalRecommendations 포맷) =====
    private String buildFallbackJson(Map<String, String> survey, List<Map<String, Object>> candidates) {
        try {
            if (candidates == null || candidates.isEmpty()) {
                return "{\"situationalRecommendations\":{}}";
            }
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
            usageRec.put("source", "fallback"); // ★ 진단

            Map<String, Object> root = Map.of("situationalRecommendations", Map.of(usage, usageRec));
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            log.error("Fallback JSON 생성 실패", e);
            return "{\"situationalRecommendations\":{}}";
        }
    }
}
