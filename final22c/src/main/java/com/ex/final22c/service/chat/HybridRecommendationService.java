package com.ex.final22c.service.chat;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ex.final22c.data.product.Product;
import com.ex.final22c.data.recommendation.RecommendedProduct;
import com.ex.final22c.data.recommendation.SituationalRecommendation;
import com.ex.final22c.repository.productMapper.ProductMapper;
import com.ex.final22c.repository.user.UserRepository;
import com.ex.final22c.service.product.ProductService;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * HybridRecommendationService
 * 
 * MYTYPE 시스템의 핵심 서비스 - AI 기반 개인화 향수 추천
 * 
 * 주요 기능:
 * 1. 설문 답변을 통한 후보 상품 필터링
 * 2. DeepSeek AI를 활용한 개인화 추천 생성
 * 3. JSON 응답 검증 및 정규화
 * 4. AI 실패 시 폴백 추천 시스템
 * 5. 상품 메타데이터 보강 및 결과 파싱
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HybridRecommendationService {

    // 의존성 주입 - AI 분석 및 데이터 처리에 필요한 서비스들
    private final ChatService chatService;          // DeepSeek AI 호출 서비스
    private final ProductMapper productMapper;      // 상품 DB 매퍼
    private final ProductService productService;    // 상품 비즈니스 로직 서비스
    private final ObjectMapper objectMapper;        // JSON 파싱 및 직렬화

    // ===================== 내부 유틸리티 메서드 =====================
    
    /**
     * 설문 기반 후보 상품 선별
     * 
     * 사용자 설문 답변을 기반으로 DB에서 적합한 후보 상품들을 필터링
     * 성별, 가격대, 메인노트, 지속력(등급) 조건으로 쿼리 최적화
     * 
     * @param survey 사용자 설문 답변 맵
     * @return 필터링된 후보 상품 리스트 (최대 200개 제한)
     */
    private List<Map<String, Object>> getFilteredCandidates(Map<String, String> survey) {
        // 설문 답변에서 필터 조건 추출
        final String gender     = survey.get("gender");       // male/female/null
        final String priceRange = survey.get("priceRange");   // low/medium/high

        // 선호 향을 DB ID로 변환 (예: "floral" → 6)
        final Integer mainNoteId    = convertNoteToId(survey.get("notes"));
        final List<Integer> mainNoteIds = (mainNoteId != null) ? List.of(mainNoteId) : null;

        // 지속력을 등급 ID 리스트로 변환 (예: "오드 퍼퓸" → [3])
        final List<Integer> gradeIds = getGradeIdsByIntensity(survey.get("intensity"));

        // DB에서 조건에 맞는 후보 상품 조회
        List<Map<String, Object>> candidates =
            productMapper.selectProductsForRecommendation(
                null,          // brandIds - 브랜드 제한 없음
                gradeIds,      // 지속력 기반 등급 필터
                mainNoteIds,   // 선호 향 필터
                null,          // volumeIds - 용량 제한 없음
                null,          // keyword - 키워드 검색 없음
                gender,        // 성별 기반 정렬 가중치
                priceRange     // 가격대 필터
            )
            .stream()
            // 재고가 있는 상품만 필터링 (COUNT > 0)
            .filter(p -> {
                Object v = p.get("count");
                if (v instanceof Number) return ((Number) v).intValue() > 0;
                if (v != null) try { return Integer.parseInt(String.valueOf(v)) > 0; } catch (Exception ignore) {}
                return true; // null일 경우 기본적으로 포함
            })
            .limit(200)  // AI 프롬프트 크기 제한으로 안정성 확보
            .collect(Collectors.toList());

        // 디버깅용 로그 - 필터링 결과 기록
        log.debug("filteredCandidates gender={}, priceRange={}, gradeIds={}, mainNoteIds={}, size={}",
            gender, priceRange, gradeIds, mainNoteIds, candidates.size());

        return candidates;
    }
    
    /**
     * MYTYPE 설문 분석 - 메인 엔트리 포인트
     * 
     * 사용자 설문 답변을 받아 AI 기반 개인화 추천을 생성하는 핵심 메서드
     * 다층 방어 구조로 안정성 확보 (AI 실패 시 폴백 처리)
     * 
     * @param surveyAnswers 사용자 설문 답변 (gender, usage, priceRange, notes, intensity)
     * @return JSON 문자열 형태의 추천 결과 (situationalRecommendations 구조)
     */
    @Transactional(readOnly = true)
    public String analyzeSurvey(Map<String, String> surveyAnswers) {
        // 1. 입력 데이터 안전성 검증 - null 방어 처리
        final Map<String, String> safe =
            (surveyAnswers == null) ? Collections.emptyMap() : surveyAnswers;

        // 2. 설문 기반으로 후보 상품 필터링 (DB 쿼리)
        List<Map<String, Object>> candidates = getFilteredCandidates(safe);

        // 3. AI 분석 실행 및 오류 처리
        String aiJson;
        boolean usedFallback = false; // 진단용 플래그 - 폴백 사용 여부 추적
        
        try {
            // DeepSeek AI를 통한 개인화 추천 생성
            aiJson = callAiAnalysis(safe, candidates);
        } catch (Exception e) {
            // AI 호출 실패 시 (네트워크 오류, API 한도 초과, 타임아웃 등)
            log.warn("AI 호출 실패. fallback JSON으로 대체합니다.", e);
            aiJson = null;
        }
        
        // 4. AI 응답 검증 및 후처리
        if (aiJson == null || aiJson.isBlank()) {
            // AI 응답이 없는 경우 → 폴백 추천 생성
            usedFallback = true;
            aiJson = buildFallbackJson(safe, candidates);
        } else {
            // AI 응답이 있는 경우 → JSON 정규화 및 유효성 검사
            aiJson = normalizeJson(aiJson);
            if (!isValidJson(aiJson)) {
                // JSON 구조가 잘못된 경우 → 폴백 추천 생성
                log.warn("AI JSON이 유효하지 않음. fallback JSON 사용");
                usedFallback = true;
                aiJson = buildFallbackJson(safe, candidates);
            }
        }

        // 5. 처리 결과 로깅 (성능 모니터링 및 디버깅용)
        log.info("analyzeSurvey 완료 - usedFallback={}, candidatesCount={}", usedFallback, candidates.size());
        return aiJson;
    }

    /**
     * AI 프롬프트 구성 및 DeepSeek AI 호출
     * 
     * 사용자 설문과 후보 상품을 바탕으로 구조화된 프롬프트를 생성하고
     * DeepSeek AI에 개인화 추천을 요청하는 메서드
     * 
     * @param survey 사용자 설문 답변
     * @param candidates 필터링된 후보 상품 리스트
     * @return AI가 생성한 JSON 추천 결과
     */
    private String callAiAnalysis(Map<String, String> survey, List<Map<String, Object>> candidates) {
        // 사용 용도 추출 (기본값: daily)
        String usage = survey.getOrDefault("usage", "daily");

        // 용도별 맞춤 추천 가이드라인 설정
        String usageGuide = switch (usage) {
            case "office" -> """
                - 사무실/밀폐 공간에 무리 없는 확산력과 잔향(EDT~EDP 중도).
                - 너무 달거나 과자향(gourmand) 계열은 피하고, 깔끔/프로페셔널 인상 우선.
                - 향 지속력은 4~7시간 선호, 가격은 과시적이지 않은 중간대 선호.
                """;
            case "special" -> """
                - 존재감 있는 시그니처, 관능적/개성 강조(EDP~Parfum 우선).
                - 스파이시/우디/플로럴의 인상적인 조합 선호. 프로젝트성 향도 고려.
                - 가격 제약보다 향의 임팩트/스토리 우선.
                """;
            case "gift" -> """
                - 선물 호불호 적고 브랜드 인지도/패키지 완성도 중요.
                - 안전한 플로랄/시트러스/머스크 계열 우선. 성별 보편/유니섹스도 가점.
                - 가격대는 사용자가 고른 범위를 반드시 존중.
                """;
            default /* daily */ -> """
                - 데일리로 부담 없는 잔향과 세탁감/클린한 계열 선호(EDT~EDP).
                - 과한 스파이시/스모키는 지양. 가격/용량 대비 가성비 고려.
                """;
        };

        // AI 프롬프트 구성 - 구조화된 지시사항
        StringBuilder prompt = new StringBuilder();
        prompt.append("당신은 전자상거래 향수 추천 시스템입니다.\n")
              .append("반드시 완전한 JSON으로만 응답하세요. 설명/코드펜스 금지.\n")
              .append("아래 후보들(productId)에서만 고르고, 숫자 타입으로 기입하세요.\n\n")

              .append("=== 사용자 설문 ===\n");
        // 설문 답변을 프롬프트에 포함
        survey.forEach((k, v) -> prompt.append(k).append(": ").append(v).append("\n"));

        prompt.append("\n=== 용도별 선택 규칙(강제) ===\n")
              .append(usageGuide).append("\n")
              .append("- 동점이면 최근 판매량/인지도 높은 제품을 우선.\n")
              .append("- 같은 브랜드 중복은 피하고, 서로 다른 캐릭터로 2개 이상 제시.\n")
              .append("- 각 제품의 선택 이유에 용도와 설문 요소(노트/강도/가격대)를 반드시 연결해 서술.\n")

              .append("\n=== 후보 상품(이 중에서만 선택) ===\n");
        // 후보 상품 정보를 프롬프트에 포함 (ID, 이름, 브랜드, 가격)
        candidates.forEach(p -> prompt.append("id=").append(p.get("id"))
            .append(", name=").append(p.get("name"))
            .append(", brand=").append(p.get("brandName"))
            .append(", price=").append(p.get("sellPrice"))
            .append("\n"));

        prompt.append("\n=== 필수 출력 형식(예시 스키마, 이 구조만 허용) ===\n")
              .append("{\"situationalRecommendations\":{\"").append(usage).append("\":{")
              .append("\"reason\":\"요약 및 선택 기준\",")
              .append("\"products\":[")
              .append("{\"productId\":숫자,\"reason\":\"왜 이 용도/설문에 맞는지\"},")
              .append("{\"productId\":숫자,\"reason\":\"왜 이 용도/설문에 맞는지\"}")
              .append("]}}}\n")
              .append("추가 텍스트 금지. JSON만 반환.\n");

        try {
            // ChatService를 통해 DeepSeek AI 호출
            String raw = chatService.ask(prompt.toString());
            String normalized = normalizeJson(raw);
            
            // JSON 구조가 잘못된 경우 텍스트에서 추출 시도
            if (!isValidJson(normalized)) {
                normalized = extractSituationalRecommendationsFromText(raw, usage);
            }
            return normalized;
        } catch (Exception e) {
            log.error("AI 호출 또는 후처리 실패", e);
            throw e; // 상위 메서드에서 폴백 처리
        }
    }

    /**
     * JSON 응답 정규화
     * 
     * AI가 반환한 원본 응답을 파싱 가능한 JSON 형태로 정리
     * 코드펜스 제거, 중괄호 균형 맞추기, 트레일링 콤마 제거 등
     * 
     * @param raw AI의 원본 응답 텍스트
     * @return 정규화된 JSON 문자열
     */
    private String normalizeJson(String raw) {
        if (raw == null || raw.trim().isEmpty()) return "{}";
        String s = raw.trim();
        log.debug("정규화 전: {}", s);

        // 1. 마크다운 코드펜스 제거 (```json ... ```)
        if (s.startsWith("```")) {
            s = s.replaceFirst("^```(?:json)?\\s*", "")
                 .replaceFirst("\\s*```\\s*$", "").trim();
        }

        // 2. 이미 완전한 JSON처럼 보이면 최소 정리만 수행
        if (s.startsWith("{") && s.endsWith("}")) {
            return s.replaceAll(",\\s*([}\\]])", "$1"); // 트레일링 콤마 제거
        }

        // 3. JSON 시작점 찾기 (설명 텍스트 이후의 JSON 부분 추출)
        int firstBrace = s.indexOf('{');
        if (firstBrace == -1) {
            log.warn("JSON에서 시작 중괄호를 찾을 수 없음");
            return "{}";
        }
        s = s.substring(firstBrace);

        // 4. 중괄호 균형 맞추기 (depth tracking으로 JSON 끝 찾기)
        int depth = 0;
        int endIndex = s.length() - 1;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '{') depth++;
            else if (ch == '}') {
                depth--;
                if (depth == 0) { 
                    endIndex = i; 
                    break; 
                }
            }
        }

        // 5. JSON 끝 이후 잡음 제거
        if (endIndex < s.length() - 1) {
            s = s.substring(0, endIndex + 1);
        }

        // 6. 트레일링 콤마 제거 (,} → })
        s = s.replaceAll(",\\s*([}\\]])", "$1");
        log.debug("정규화 후: {}", s);
        return s;
    }

    /**
     * JSON 유효성 검사
     * 
     * 정규화된 JSON이 파싱 가능하고 예상된 구조(situationalRecommendations)를 
     * 가지고 있는지 검증
     * 
     * @param s 검증할 JSON 문자열
     * @return 유효한 JSON이면 true, 아니면 false
     */
    private boolean isValidJson(String s) {
        if (s == null || s.trim().isEmpty()) return false;
        try {
            // Jackson ObjectMapper로 파싱 시도
            Map<String, Object> parsed = objectMapper.readValue(s, Map.class);
            
            // situationalRecommendations 키가 있고 내용이 있는지 확인
            if (parsed.containsKey("situationalRecommendations")) {
                Object recs = parsed.get("situationalRecommendations");
                if (recs instanceof Map) {
                    return !((Map<?, ?>) recs).isEmpty();
                }
            }
            return true; // 다른 구조여도 유효한 JSON이면 통과
        } catch (Exception e) {
            log.debug("JSON 유효성 검사 실패: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 텍스트에서 JSON 추출
     * 
     * AI 응답이 완전한 JSON이 아닌 경우 텍스트에서 
     * situationalRecommendations 부분만 추출하여 JSON 구성
     * 
     * @param rawText AI의 원본 응답 텍스트
     * @param targetUsage 대상 사용 용도
     * @return 추출된 JSON 문자열
     */
    private String extractSituationalRecommendationsFromText(String rawText, String targetUsage) {
        try {
            // "situationalRecommendations" 키워드 찾기
            int startIndex = rawText.indexOf("\"situationalRecommendations\"");
            if (startIndex == -1) {
                return createSimpleRecommendationJson(rawText, targetUsage);
            }
            
            // 중괄호 시작점 찾기
            int braceStart = rawText.indexOf('{', startIndex);
            if (braceStart == -1) return createSimpleRecommendationJson(rawText, targetUsage);

            // 중괄호 균형을 통해 JSON 끝점 찾기
            int depth = 0;
            int endIndex = braceStart;
            for (int i = braceStart; i < rawText.length(); i++) {
                char ch = rawText.charAt(i);
                if (ch == '{') depth++;
                else if (ch == '}') {
                    depth--;
                    if (depth == 0) { 
                        endIndex = i; 
                        break; 
                    }
                }
            }
            
            // 완전한 JSON 구조 생성
            String extracted = "{\"situationalRecommendations\":" +
                               rawText.substring(braceStart, endIndex + 1) + "}";
            extracted = extracted.replaceAll(",\\s*([}\\]])", "$1"); // 트레일링 콤마 제거
            log.debug("추출된 JSON: {}", extracted);
            return extracted;

        } catch (Exception e) {
            log.warn("텍스트에서 JSON 추출 실패: {}", e.getMessage());
            return createSimpleRecommendationJson(rawText, targetUsage);
        }
    }

    /**
     * 간단한 추천 JSON 생성 (최후 폴백)
     * 
     * 모든 파싱 시도가 실패한 경우 텍스트에서 productId만 추출하여
     * 기본적인 추천 JSON 구조를 생성
     * 
     * @param rawText AI의 원본 응답 텍스트
     * @param usage 사용 용도
     * @return 기본 구조의 JSON 문자열
     */
    private String createSimpleRecommendationJson(String rawText, String usage) {
        try {
            // 정규식으로 productId 추출 ("productId": 123 패턴)
            var pattern = java.util.regex.Pattern.compile("\"productId\"\\s*:\\s*(\\d+)");
            var matcher = pattern.matcher(rawText);

            // 최대 3개까지 productId 수집
            List<Map<String, Object>> products = new java.util.ArrayList<>();
            while (matcher.find() && products.size() < 3) {
                long productId = Long.parseLong(matcher.group(1));
                products.add(Map.of("productId", productId, "reason", "AI 분석 결과에서 추출된 추천"));
            }
            
            // productId를 찾지 못한 경우 기본값 설정
            if (products.isEmpty()) {
                products = List.of(
                    Map.of("productId", 1, "reason", "기본 추천 상품"),
                    Map.of("productId", 2, "reason", "기본 추천 상품")
                );
            }

            // 표준 JSON 구조 생성
            Map<String, Object> situationData = new HashMap<>();
            situationData.put("reason", "AI 분석 결과를 기반한 추천");
            situationData.put("products", products);
            situationData.put("source", "fallback"); // 진단용 플래그

            Map<String, Object> situationalRecs = new HashMap<>();
            situationalRecs.put(usage != null ? usage : "daily", situationData);

            Map<String, Object> root = new HashMap<>();
            root.put("situationalRecommendations", situationalRecs);

            return objectMapper.writeValueAsString(root);

        } catch (Exception e) {
            log.error("간단한 추천 JSON 생성 실패", e);
            // 최종 폴백 - 빈 구조 반환
            return "{\"situationalRecommendations\":{\"daily\":{\"reason\":\"추천 결과를 불러올 수 없습니다\",\"products\":[],\"source\":\"fallback\"}}}";
        }
    }

    /**
     * AI 응답에서 상품 ID 추출
     * 
     * JSON 응답을 파싱하여 추천된 모든 상품의 ID를 추출하고
     * 콤마로 구분된 문자열로 반환 (예: "1,3,7")
     * 
     * @param aiResult AI 응답 JSON 문자열
     * @return 콤마로 구분된 상품 ID 문자열
     */
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
            
            // 모든 상황별 추천에서 productId 수집
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
                            } catch (NumberFormatException e) { 
                                log.warn("productId 파싱 실패: {}", obj); 
                            }
                        }
                    });
                }
            }
            return ids.stream().map(String::valueOf).collect(Collectors.joining(","));
        } catch (Exception e) {
            log.error("상품 ID 추출 실패", e);
            return extractProductIdsFromText(aiResult); // 폴백으로 텍스트 파싱 시도
        }
    }

    /**
     * 텍스트에서 직접 상품 ID 추출 (폴백)
     * 
     * JSON 파싱이 실패한 경우 정규식을 사용하여
     * 텍스트에서 직접 productId 패턴을 찾아 추출
     * 
     * @param text 분석할 텍스트
     * @return 콤마로 구분된 상품 ID 문자열
     */
    private String extractProductIdsFromText(String text) {
        try {
            var pattern = java.util.regex.Pattern.compile("\"productId\"\\s*:\\s*(\\d+)");
            var matcher = pattern.matcher(text);
            Set<String> ids = new java.util.LinkedHashSet<>(); // 순서 유지
            while (matcher.find()) ids.add(matcher.group(1));
            return String.join(",", ids);
        } catch (Exception e) {
            log.error("텍스트에서 productId 추출 실패", e);
            return "";
        }
    }

    /**
     * 파싱된 추천 결과 조회 (프론트엔드용)
     * 
     * AI 응답 JSON을 파싱하여 특정 상황(usage)에 대한
     * 추천 결과를 SituationalRecommendation 객체로 변환
     * 
     * @param aiJson AI 응답 JSON 문자열
     * @param situation 조회할 상황 (daily, office, special, gift)
     * @return 파싱된 추천 결과 객체
     */
    public SituationalRecommendation getParsedRecommendation(String aiJson, String situation) {
        try {
            String cleaned = normalizeJson(aiJson);
            Map<String, Object> analysis = objectMapper.readValue(cleaned, Map.class);
            Map<String, Object> recs =
                (Map<String, Object>) analysis.get("situationalRecommendations");
                
            // 해당 상황의 추천이 없는 경우 빈 결과 반환
            if (recs == null || !recs.containsKey(situation)) {
                return getEmptyRecommendation(situation);
            }

            Map<String, Object> sitData = (Map<String, Object>) recs.get(situation);
            List<Map<String, Object>> prodData =
                (List<Map<String, Object>>) sitData.get("products");
                
            // 상품 정보를 RecommendedProduct 객체로 변환 (메타데이터 보강)
            var products = prodData.stream()
                .map(this::mapToRecommendedProduct)
                .filter(p -> p != null) // 변환 실패한 항목 제외
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

    /**
     * 상품 정보 매핑 (메타데이터 보강)
     * 
     * AI 추천 결과의 기본 정보에 실제 상품 데이터를 보강하여
     * 완전한 RecommendedProduct 객체를 생성
     * 
     * @param pd AI 추천 결과의 상품 데이터 맵
     * @return 메타데이터가 보강된 RecommendedProduct 객체
     */
    /**
     * 상품 정보 매핑 (메타데이터 보강)
     * 
     * AI 추천 결과의 기본 정보에 실제 상품 데이터를 보강하여
     * 완전한 RecommendedProduct 객체를 생성
     * 
     * @param pd AI 추천 결과의 상품 데이터 맵
     * @return 메타데이터가 보강된 RecommendedProduct 객체
     */
    private RecommendedProduct mapToRecommendedProduct(Map<String, Object> pd) {
        try {
            // productId 추출 및 타입 변환 (Number 또는 String → Long)
            Object obj = pd.get("productId");
            long id = (obj instanceof Number)
                ? ((Number) obj).longValue()
                : Long.parseLong(obj.toString());

            // 실제 상품 정보 조회 (DB에서 메타데이터 가져오기)
            Product p = productService.getProduct(id);
            if (p == null) return null; // 상품이 존재하지 않으면 null 반환

            // 이미지 URL 생성 (경로 + 파일명 조합)
            String img = "/img/noimg.png"; // 기본 이미지
            if (p.getImgPath() != null && p.getImgName() != null) {
                String path = p.getImgPath();
                if (!path.endsWith("/")) path += "/"; // 경로 끝에 슬래시 추가
                img = path + p.getImgName();
            }

            // RecommendedProduct 객체 생성 (Builder 패턴)
            return RecommendedProduct.builder()
                .productId(id)
                .name(p.getName())
                .brandName(p.getBrand() != null ? p.getBrand().getBrandName() : "")
                .volume((String) pd.get("volume"))       // AI 추천에서 제공된 용량 정보
                .price(p.getSellPrice())                 // 실제 판매가격
                .reason((String) pd.get("reason"))       // AI가 제공한 추천 이유
                .imageUrl(img)                           // 생성된 이미지 URL
                .build();

        } catch (Exception e) {
            log.error("상품 매핑 실패", e);
            return null; // 매핑 실패 시 null 반환 (stream filter에서 제외됨)
        }
    }

    /**
     * 빈 추천 결과 생성
     * 
     * 추천 데이터가 없거나 파싱에 실패한 경우
     * 기본 메시지가 포함된 빈 추천 결과를 반환
     * 
     * @param situation 상황 코드
     * @return 빈 추천 결과 객체
     */
    private SituationalRecommendation getEmptyRecommendation(String situation) {
        return SituationalRecommendation.builder()
            .situation(situation)
            .products(Collections.emptyList())      // 빈 상품 리스트
            .analysis("설문조사를 먼저 완료해주세요")  // 기본 안내 메시지
            .build();
    }
    
    /**
     * 선호 향을 데이터베이스 ID로 변환
     * 
     * 사용자가 선택한 향 이름을 DB의 MAINNOTE 테이블 ID로 매핑
     * 한국어와 영어 모두 지원
     * 
     * @param note 선호 향 이름 (floral, 플로랄 등)
     * @return 해당하는 DB ID, 없으면 기본값 6 (플로랄)
     */
    private Integer convertNoteToId(String note) {
        if (note == null) return 6; // null인 경우 기본값: 플로랄
        
        // 향 이름 → DB ID 매핑 테이블
        Map<String, Integer> m = new HashMap<>();
        m.put("floral", 6);   m.put("플로랄", 6);     // 플로랄 계열
        m.put("citrus", 7);   m.put("시트러스", 7);   // 시트러스 계열
        m.put("woody",  2);   m.put("우디", 2);       // 우디 계열
        m.put("spicy",  1);   m.put("스파이시", 1);   // 스파이시 계열
        m.put("fruity", 4);   m.put("프루티", 4);     // 프루티 계열
        m.put("herbal", 3);   m.put("허벌", 3);       // 허벌 계열
        
        return m.getOrDefault(note.toLowerCase(), 6); // 매칭되지 않으면 기본값
    }

    /**
     * 지속력을 등급 ID 리스트로 변환
     * 
     * 사용자가 선택한 지속력 옵션을 DB의 GRADE 테이블 ID로 매핑
     * 향수의 농도에 따른 지속력 분류
     * 
     * @param intensity 지속력 옵션 (오드 코롱, 오드 뚜왈렛 등)
     * @return 해당하는 등급 ID 리스트
     */
    private List<Integer> getGradeIdsByIntensity(String intensity) {
        if (intensity == null || intensity.isBlank()) {
            return List.of(3, 4); // 기본값: 오드 퍼퓸 + 오드 뚜왈렛
        }
        
        String k = intensity.trim().toLowerCase();

        // 지속력 → 등급 ID 매핑 (낮은 농도부터 높은 농도까지)
        switch (k) {
            case "오드 코롱": case "eau de cologne": case "light":
                return List.of(2);      // 가장 가벼운 농도 (2-3시간)
            case "오드 뚜왈렛": case "eau de toilette": case "medium":
                return List.of(4);      // 중간 농도 (3-5시간)
            case "오드 퍼퓸": case "eau de parfum": case "strong":
                return List.of(3);      // 진한 농도 (5-8시간)
            case "퍼퓸": case "parfum":
                return List.of(1);      // 가장 진한 농도 (8시간 이상)
            default:
                return List.of(3, 4);   // 인식되지 않는 경우 중간~진한 농도
        }
    }

    /**
     * 폴백 추천 JSON 생성
     * 
     * AI 분석이 실패했을 때 사용할 기본 추천을 생성
     * 필터링된 후보 상품에서 랜덤하게 선택하여 추천 구성
     * 
     * @param survey 사용자 설문 답변
     * @param candidates 필터링된 후보 상품 리스트
     * @return 폴백 추천 JSON 문자열
     */
    private String buildFallbackJson(Map<String, String> survey, List<Map<String, Object>> candidates) {
        try {
            // 후보가 없는 경우 빈 JSON 반환
            if (candidates == null || candidates.isEmpty()) {
                return "{\"situationalRecommendations\":{}}";
            }
            
            // 후보 상품을 랜덤하게 섞고 최대 3개 선택
            Collections.shuffle(candidates);
            List<Map<String, Object>> picks = candidates.stream().limit(3).collect(Collectors.toList());

            // 사용 용도 추출 (기본값: daily)
            String usage = survey.getOrDefault("usage", "daily");
            
            // 선택된 상품들을 추천 형식으로 변환
            List<Map<String, Object>> prods = picks.stream().map(p -> Map.of(
                "productId", p.get("id"),
                "reason", "설문 조건과 유사한 후보에서 추천" // 기본 추천 이유
            )).collect(Collectors.toList());

            // 상황별 추천 데이터 구성
            Map<String, Object> usageRec = new HashMap<>();
            usageRec.put("reason", "설문 조건에 맞는 상품들을 추천드립니다"); // 전체 추천 이유
            usageRec.put("products", prods);                          // 추천 상품 리스트
            usageRec.put("source", "fallback");                      // 진단용 플래그

            // 최종 JSON 구조 생성
            Map<String, Object> root = Map.of("situationalRecommendations", Map.of(usage, usageRec));
            return objectMapper.writeValueAsString(root);
            
        } catch (Exception e) {
            log.error("Fallback JSON 생성 실패", e);
            // 최종 실패 시 최소한의 구조 반환
            return "{\"situationalRecommendations\":{}}";
        }
    }
}