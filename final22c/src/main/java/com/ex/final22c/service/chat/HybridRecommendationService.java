package com.ex.final22c.service.chat;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.HashSet;
import java.util.HashMap;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import com.ex.final22c.data.product.Product;
import com.ex.final22c.data.recommendation.RecommendedProduct;
import com.ex.final22c.data.recommendation.SituationalRecommendation;
import com.ex.final22c.data.recommendation.UserActivityData;
import com.ex.final22c.data.user.UserPreference;
import com.ex.final22c.data.user.Users;
import com.ex.final22c.repository.productMapper.ProductMapper;
import com.ex.final22c.repository.user.UserPreferenceRepository;
import com.ex.final22c.repository.user.UserRepository;
import com.ex.final22c.service.order.OrderService;
import com.ex.final22c.service.product.ProductService;
import com.ex.final22c.service.product.ReviewService;
import com.ex.final22c.service.product.ZzimService;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class HybridRecommendationService {
    
    private final ChatService chatService;
    private final UserPreferenceRepository userPreferenceRepository;
    private final UserRepository userRepository;
    private final ProductMapper productMapper;
    private final ProductService productService;
    private final ZzimService zzimService;
    private final ReviewService reviewService;
    private final OrderService orderService;
    private final ObjectMapper objectMapper;
    
    @Transactional
    public UserPreference analyzeUserWithAI(String userName, Map<String, String> surveyAnswers) {
        
        Users user = userRepository.findByUserName(userName)
            .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다"));
        
        try {
            // 1. 설문 답변으로 후보군 필터링
            List<Map<String, Object>> candidates = getFilteredCandidates(surveyAnswers);
            
            // 2. 사용자 활동 데이터 수집
            UserActivityData activity = collectUserActivity(userName);
            
            // 3. AI 분석 실행
            String aiResult = callAiAnalysis(surveyAnswers, activity, candidates);
            
            // 4. 결과 저장
            return saveOrUpdatePreference(user, surveyAnswers, aiResult);
            
        } catch (Exception e) {
            log.error("AI 분석 실패: {}", userName, e);
            return createFallbackRecommendation(user, surveyAnswers);
        }
    }
    
    private List<Map<String, Object>> getFilteredCandidates(Map<String, String> survey) {
        Long mainNoteId = convertNoteToId(survey.get("notes"));
        List<Long> gradeIds = getGradeIdsByIntensity(survey.get("intensity"));
        
        return productMapper.selectProducts(null, gradeIds, List.of(mainNoteId), null, null)
            .stream()
            .filter(p -> {
                Integer count = (Integer) p.get("count");
                return count != null && count > 0;
            })
            .limit(15)
            .collect(Collectors.toList());
    }
    
    private UserActivityData collectUserActivity(String userName) {
        return UserActivityData.builder()
            .zzimProducts(zzimService.listMyZzim(userName))
            .reviews(reviewService.getReviewsByUser(userName))
            .purchases(orderService.getUserPurchaseHistory(userName))
            .build();
    }
    
    private String callAiAnalysis(Map<String, String> survey, UserActivityData activity, 
                                 List<Map<String, Object>> candidates) {
        String prompt = buildAnalysisPrompt(survey, activity, candidates);
        return chatService.ask(prompt);
    }
    
    private String buildAnalysisPrompt(Map<String, String> survey, UserActivityData activity, 
                                     List<Map<String, Object>> candidates) {
        StringBuilder prompt = new StringBuilder();
        
        prompt.append("사용자 향수 추천을 위한 AI 분석을 요청합니다.\n\n");
        
        prompt.append("=== 사용자 설문조사 결과 ===\n");
        survey.forEach((key, value) -> {
            prompt.append(key).append(": ").append(value).append("\n");
        });
        
        prompt.append("\n=== 사용자 활동 데이터 ===\n");
        prompt.append("찜한 상품 수: ").append(activity.getZzimProducts().size()).append("\n");
        prompt.append("작성한 리뷰 수: ").append(activity.getReviews().size()).append("\n");
        prompt.append("구매 이력 수: ").append(activity.getPurchases().size()).append("\n");
        
        // 찜한 상품 상세 정보
        if (!activity.getZzimProducts().isEmpty()) {
            prompt.append("\n찜한 상품들:\n");
            activity.getZzimProducts().forEach(product -> {
                prompt.append("- ").append(product.getName())
                      .append(" (").append(product.getBrand().getBrandName()).append(")")
                      .append("\n");
            });
        }
        
        // 리뷰 작성 상품들
        if (!activity.getReviews().isEmpty()) {
            prompt.append("\n리뷰를 작성한 상품들:\n");
            activity.getReviews().forEach(review -> {
                prompt.append("- ").append(review.getProduct().getName())
                      .append(" (평점: ").append(review.getRating()).append("/5)")
                      .append("\n");
            });
        }
        
        prompt.append("\n=== 추천 후보 상품들 ===\n");
        candidates.forEach(product -> {
            prompt.append("상품ID: ").append(product.get("id")).append(" - ").append(product.get("name")).append(" (")
            	  .append(product.get("brandName")).append(")\n")
                  .append(", 이름: ").append(product.get("name"))
                  .append(", 브랜드: ").append(product.get("brandName"))
                  .append(", 용량: ").append(product.get("volume"))
                  .append(", 가격: ").append(product.get("sellPrice"))
                  .append("\n");
        });
        
        prompt.append("\n=== 요청사항 ===\n");
        prompt.append("위 정보를 바탕으로 사용자에게 맞는 향수를 상황별로 추천해주세요.\n");
        prompt.append("다음 세 가지 상황에 대해 각각 2-3개의 상품을 추천하고 이유를 설명해주세요:\n");
        prompt.append("1. daily: 일상적으로 사용할 향수\n");
        prompt.append("2. special: 특별한 날이나 데이트용 향수\n");
        prompt.append("3. gift: 선물용으로 적합한 향수\n\n");
        
        prompt.append("응답은 반드시 다음 JSON 형식으로만 작성해주세요 (중요: productId는 반드시 숫자로!):\n");
        prompt.append("{\n");
        prompt.append("  \"situationalRecommendations\": {\n");
        prompt.append("    \"daily\": {\n");
        prompt.append("      \"products\": [\n");
        prompt.append("        {\"productId\": 1, \"volume\": \"50ml\", \"reason\": \"가벼운 시트러스 향으로 일상에 적합\"},\n");
        prompt.append("        {\"productId\": 2, \"volume\": \"30ml\", \"reason\": \"은은한 플로럴 향으로 오피스용으로 좋음\"}\n");
        prompt.append("      ],\n");
        prompt.append("      \"reason\": \"일상용 향수 추천 전체 이유\"\n");
        prompt.append("    },\n");
        prompt.append("    \"special\": {\n");
        prompt.append("      \"products\": [\n");
        prompt.append("        {\"productId\": 3, \"volume\": \"100ml\", \"reason\": \"강렬한 우디 향으로 특별한 날에 적합\"}\n");
        prompt.append("      ],\n");
        prompt.append("      \"reason\": \"특별한 날 향수 추천 전체 이유\"\n");
        prompt.append("    },\n");
        prompt.append("    \"gift\": {\n");
        prompt.append("      \"products\": [\n");
        prompt.append("        {\"productId\": 4, \"volume\": \"75ml\", \"reason\": \"누구나 좋아할 만한 클래식한 향\"}\n");
        prompt.append("      ],\n");
        prompt.append("      \"reason\": \"선물용 향수 추천 전체 이유\"\n");
        prompt.append("    }\n");
        prompt.append("  }\n");
        prompt.append("}\n");
        prompt.append("\n**중요: 위에 나열된 상품ID만 사용해서 추천해주세요. 다른 ID는 절대 사용하지 마세요.**\n");
        
        return prompt.toString();
    }
    
    private UserPreference saveOrUpdatePreference(Users user, Map<String, String> survey, String aiResult) {
        
        UserPreference preference = userPreferenceRepository.findByUser_UserName(user.getUserName())
            .orElse(UserPreference.builder().user(user).build());
            
        try {
            preference.setSurveyAnswers(objectMapper.writeValueAsString(survey));
            preference.setAiAnalysis(aiResult);
            preference.setRecommendedProducts(extractProductIds(aiResult));
        } catch (Exception e) {
            log.error("결과 저장 실패", e);
        }
        
        return userPreferenceRepository.save(preference);
    }
    
    /**
     * 상황별 추천 조회 (저장된 결과에서)
     */
    public SituationalRecommendation getSituationalRecommendation(String userName, String situation) {
        UserPreference preference = userPreferenceRepository.findByUser_UserName(userName)
            .orElse(null);

        if (preference == null) {
            return getEmptyRecommendation(situation);
        }

        try {
            // 1. JSON 전처리
            String rawJson = preference.getAiAnalysis();
            String cleanedJson = rawJson.trim().replaceAll("^[^\\{]+", ""); // '{' 이전 쓰레기 문자 제거

            // 2. 디버깅용 로그
            log.debug("📦 JSON 파싱 대상: {}", cleanedJson);

            // 3. JSON 파싱
            Map<String, Object> analysis = objectMapper.readValue(cleanedJson, Map.class);
            Map<String, Object> situationalRecs = (Map<String, Object>) analysis.get("situationalRecommendations");

            if (situationalRecs == null || !situationalRecs.containsKey(situation)) {
                return getEmptyRecommendation(situation);
            }

            Map<String, Object> situationData = (Map<String, Object>) situationalRecs.get(situation);
            List<Map<String, Object>> productData = (List<Map<String, Object>>) situationData.get("products");

            List<RecommendedProduct> products = productData.stream()
                .map(this::mapToRecommendedProduct)
                .filter(p -> p != null)
                .collect(Collectors.toList());

            return SituationalRecommendation.builder()
                .situation(situation)
                .products(products)
                .analysis((String) situationData.get("reason"))
                .build();

        } catch (Exception e) {
            log.error("추천 결과 파싱 실패", e);
            return getEmptyRecommendation(situation);
        }
    }

    /**
     * 상품 매핑 메서드 - 수정된 버전 (문자열 productId 처리)
     */
    private RecommendedProduct mapToRecommendedProduct(Map<String, Object> productData) {
        try {
            Object productIdObj = productData.get("productId");
            Long productId = null;
            
            // 1. productId가 숫자인지 문자열인지 확인
            if (productIdObj instanceof Number) {
                productId = ((Number) productIdObj).longValue();
            } else if (productIdObj instanceof String) {
                String productIdStr = (String) productIdObj;
                
                // 2-1. 숫자 문자열인 경우 파싱 시도
                try {
                    productId = Long.parseLong(productIdStr);
                } catch (NumberFormatException e) {
                    // 2-2. 문자열 상품명인 경우 DB에서 검색
                    productId = findProductIdByName(productIdStr);
                    if (productId == null) {
                        log.warn("상품명으로 상품을 찾을 수 없습니다: {}", productIdStr);
                        return null;
                    }
                }
            } else {
                log.warn("productId 형식이 올바르지 않습니다: {}", productIdObj);
                return null;
            }
            
            // DB에서 실제 상품 정보 조회
            Product product = productService.getProduct(productId);
            
            if (product == null) {
                log.warn("상품을 찾을 수 없습니다: {}", productId);
                return null;
            }
            
            // 이미지 URL 생성
            String imageUrl = "/img/noimg.png";
            if (product.getImgPath() != null && product.getImgName() != null) {
                String path = product.getImgPath();
                if (!path.endsWith("/")) path += "/";
                imageUrl = path + product.getImgName();
            }
            
            return RecommendedProduct.builder()
                .productId(productId)
                .name(product.getName())
                .brandName(product.getBrand() != null ? product.getBrand().getBrandName() : "")
                .volume((String) productData.get("volume"))
                .price(product.getSellPrice())
                .reason((String) productData.get("reason"))
                .imageUrl(imageUrl)
                .build();
                
        } catch (Exception e) {
            log.error("상품 매핑 실패: {}", productData, e);
            return null;
        }
    }

    /**
     * 상품명으로 productId 찾기 (새로 추가된 메서드)
     */
    private Long findProductIdByName(String productName) {
        try {
            // 1. 언더스코어로 분리된 경우 처리 (예: "diptyque_tam_dao")
            if (productName.contains("_")) {
                String[] parts = productName.split("_");
                if (parts.length >= 2) {
                    String brandName = parts[0];
                    String name = String.join(" ", java.util.Arrays.copyOfRange(parts, 1, parts.length));
                    
                    // ProductMapper를 통해 브랜드명과 상품명으로 검색
                    List<Map<String, Object>> products = productMapper.findByBrandAndName(brandName, name);
                    
                    if (!products.isEmpty()) {
                        Object idObj = products.get(0).get("productId"); // findByBrandAndName은 "productId" 반환
                        if (idObj instanceof Number) {
                            return ((Number) idObj).longValue();
                        }
                    }
                }
            }
            
            // 2. 일반 상품명으로 검색
            List<Map<String, Object>> products = productMapper.findByProductName(productName);
            
            if (!products.isEmpty()) {
                Object idObj = products.get(0).get("productId"); // findByProductName은 "productId" 반환
                if (idObj instanceof Number) {
                    return ((Number) idObj).longValue();
                }
            }
            
            return null;
        } catch (Exception e) {
            log.error("상품명으로 ID 찾기 실패: {}", productName, e);
            return null;
        }
    }
    
    // 유틸리티 메서드들
    private Long convertNoteToId(String noteName) {
        if (noteName == null) return 6L; // 기본값: floral
        
        Map<String, Long> mapping = Map.of(
            "floral", 6L, "citrus", 7L, "woody", 2L, "spicy", 1L,
            "vanilla", 8L, "fruity", 4L, "herbal", 3L, "gourmand", 5L
        );
        return mapping.getOrDefault(noteName.toLowerCase(), 6L);
    }
    
    private List<Long> getGradeIdsByIntensity(String intensity) {
        if (intensity == null) return List.of(3L, 4L); // 기본값: medium
        
        Map<String, List<Long>> mapping = Map.of(
            "light", List.of(2L, 4L),    // 오드코롱, 오드뚜왈렛
            "medium", List.of(3L, 4L),   // 오드퍼퓸, 오드뚜왈렛  
            "strong", List.of(1L, 3L)    // 퍼퓸, 오드퍼퓸
        );
        return mapping.getOrDefault(intensity.toLowerCase(), List.of(3L, 4L));
    }
    
    private String extractProductIds(String aiResult) {
        try {
            Map<String, Object> analysis = objectMapper.readValue(aiResult, Map.class);
            Map<String, Object> situationalRecs = (Map<String, Object>) analysis.get("situationalRecommendations");
            
            if (situationalRecs == null) {
                return "";
            }
            
            Set<Long> allProductIds = new HashSet<>();
            
            for (Map.Entry<String, Object> entry : situationalRecs.entrySet()) {
                Map<String, Object> situation = (Map<String, Object>) entry.getValue();
                List<Map<String, Object>> products = (List<Map<String, Object>>) situation.get("products");
                
                if (products != null) {
                    for (Map<String, Object> product : products) {
                        Object productIdObj = product.get("productId");
                        if (productIdObj != null) {
                            try {
                                // 수정: 안전한 Long 변환
                                if (productIdObj instanceof Number) {
                                    allProductIds.add(((Number) productIdObj).longValue());
                                } else if (productIdObj instanceof String) {
                                    Long id = Long.parseLong((String) productIdObj);
                                    allProductIds.add(id);
                                }
                            } catch (NumberFormatException e) {
                                log.warn("productId 파싱 실패, 건너뜀: {}", productIdObj);
                            }
                        }
                    }
                }
            }
            
            return allProductIds.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
                
        } catch (Exception e) {
            log.error("상품 ID 추출 실패", e);
            return "";
        }
    }
    
    private UserPreference createFallbackRecommendation(Users user, Map<String, String> survey) {
        try {
            // Fallback JSON 생성 - 기본 추천 상품들로 구성
            Map<String, Object> fallbackJson = new HashMap<>();
            Map<String, Object> situationalRecs = new HashMap<>();
            
            // 기본 일상용 추천
            Map<String, Object> daily = new HashMap<>();
            daily.put("products", List.of(
                Map.of("productId", 1, "volume", "50ml", "reason", "가벼운 향으로 일상에 적합"),
                Map.of("productId", 2, "volume", "30ml", "reason", "은은한 향으로 사무실에서 사용하기 좋음")
            ));
            daily.put("reason", "AI 분석이 불가하여 기본 추천을 제공합니다");
            
            // 기본 특별한 날 추천
            Map<String, Object> special = new HashMap<>();
            special.put("products", List.of(
                Map.of("productId", 3, "volume", "100ml", "reason", "특별한 날에 어울리는 매력적인 향")
            ));
            special.put("reason", "기본 특별한 날 추천");
            
            // 기본 선물 추천
            Map<String, Object> gift = new HashMap<>();
            gift.put("products", List.of(
                Map.of("productId", 4, "volume", "75ml", "reason", "누구나 좋아할 만한 무난한 향")
            ));
            gift.put("reason", "기본 선물 추천");
            
            situationalRecs.put("daily", daily);
            situationalRecs.put("special", special);
            situationalRecs.put("gift", gift);
            fallbackJson.put("situationalRecommendations", situationalRecs);
            
            return UserPreference.builder()
                .user(user)
                .surveyAnswers(objectMapper.writeValueAsString(survey))
                .aiAnalysis(objectMapper.writeValueAsString(fallbackJson))
                .recommendedProducts("1,2,3,4")
                .build();
        } catch (Exception e) {
            log.error("Fallback 추천 생성 실패", e);
            return UserPreference.builder()
                .user(user)
                .surveyAnswers(survey.toString())
                .aiAnalysis("{\"fallback\": true}")
                .recommendedProducts("")
                .build();
        }
    }
    
    private SituationalRecommendation getEmptyRecommendation(String situation) {
        return SituationalRecommendation.builder()
            .situation(situation)
            .products(Collections.emptyList())
            .analysis("설문조사를 먼저 완료해주세요")
            .build();
    }

    /**
     * 사용자 설문 완료 여부 확인
     */
    public boolean hasUserCompletedSurvey(String username) {
        Optional<Users> user = userRepository.findByUserName(username);
        if (user.isPresent()) {
            return userPreferenceRepository.existsByUser(user.get());
        }
        return false;
    }

    /**
     * 사용자 선호도 정보 조회
     */
    public UserPreference getUserPreference(String userName) {
        return userPreferenceRepository.findByUser_UserName(userName).orElse(null);
    }

    /**
     * 모든 상황별 추천 한번에 조회
     */
    public Map<String, SituationalRecommendation> getAllSituationalRecommendations(String userName) {
        Map<String, SituationalRecommendation> result = new HashMap<>();
        result.put("daily", getSituationalRecommendation(userName, "daily"));
        result.put("special", getSituationalRecommendation(userName, "special"));
        result.put("gift", getSituationalRecommendation(userName, "gift"));
        return result;
    }

    /**
     * 추천 상품 ID 리스트 조회
     */
    public List<Long> getRecommendedProductIds(String userName) {
        UserPreference preference = userPreferenceRepository.findByUser_UserName(userName).orElse(null);
        if (preference == null || preference.getRecommendedProducts() == null) {
            return Collections.emptyList();
        }

        try {
            return java.util.Arrays.stream(preference.getRecommendedProducts().split(","))
                .filter(s -> !s.trim().isEmpty())
                .map(s -> Long.parseLong(s.trim()))
                .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("추천 상품 ID 파싱 실패: {}", userName, e);
            return Collections.emptyList();
        }
    }

    /**
     * 설문 초기화 (재설문을 위한)
     */
    @Transactional
    public void resetUserSurvey(String userName) {
        Optional<Users> user = userRepository.findByUserName(userName);
        if (user.isPresent()) {
            userPreferenceRepository.deleteByUserName(userName);
            log.info("사용자 설문 데이터 초기화: {}", userName);
        }
    }
}