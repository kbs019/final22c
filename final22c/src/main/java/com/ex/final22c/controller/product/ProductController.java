package com.ex.final22c.controller.product;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.ex.final22c.data.product.Product;
import com.ex.final22c.data.product.Review;
import com.ex.final22c.data.product.ReviewDto;
import com.ex.final22c.data.user.Users;
import com.ex.final22c.repository.user.UserRepository;
import com.ex.final22c.service.chat.ChatService;
import com.ex.final22c.service.product.ProductDescriptionService;
import com.ex.final22c.service.product.ProductService;
import com.ex.final22c.service.product.ReviewService;
import com.ex.final22c.service.product.ZzimService;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;

@Slf4j
@Controller
@RequestMapping("/main")
@RequiredArgsConstructor
public class ProductController {

	
    private final ProductService productService;
    private final ZzimService zzimService;
    private final ReviewService reviewService;
    private final UserRepository userRepository;
    private final ChatService chatService;

    @GetMapping("")
    public String main(Model model) {

        // 배너(이미지 경로는 필요 시 변경)
        model.addAttribute("bannerUrl", "/img/banner/main-banner.jpg");

        // PICK 캐러셀(4개씩)
        model.addAttribute("pickSlides", productService.getPickSlides(4));

        // 전체 베스트 TOP10
        model.addAttribute("allBest", productService.getAllBest(10));

        // 여성/남성 베스트 TOP10 (Users.gender: 1=남, 2=여)
        model.addAttribute("womanBest", productService.getGenderBest("F", 10)); // ✅ 여성 = F
        model.addAttribute("manBest", productService.getGenderBest("M", 10)); // ✅ 남성 = M

        return "main/main";
    }

    // 상세
    @GetMapping("/content/{id}")
    public String productContent(
            @PathVariable("id") long id,
            @RequestParam(value = "sort", defaultValue = "best") String sort,
            @AuthenticationPrincipal UserDetails principal,
            Model model) {

        Product product = productService.getProduct(id);

        long reviewCount = reviewService.count(product);
        double avg = reviewService.avg(product);
        List<ReviewDto> reviews = reviewService.getReviews(product, sort);

        // 로그인 사용자가 누른 리뷰 id 세트(템플릿에서 상태 표시용)
        Set<Long> likedReviewIds = new HashSet<>();
        if (principal != null) {
            String me = principal.getUsername();
            for (ReviewDto rv : reviews) {
                if (rv.getLikers().stream().anyMatch(u -> me.equals(u))) { // u 자체가 String
                    likedReviewIds.add(rv.getReviewId());
                }
            }
        }

        // 로그인/찜 초기 상태 주입 --- (추가)
        boolean loggedIn = (principal != null);
        boolean zzimedByMe = false;
        String me = null;

        if (loggedIn) {
            me = principal.getUsername(); // Users.userName 과 매핑
            zzimedByMe = zzimService.isZzimed(me, id); // 초기 찜 상태
        }

        // 2) 관련/추천
        List<Object[]> brandRecs   = productService.getSameBrandRecommendations(id, 8);

        // 최근 7일 TOP 8 시도
        List<Object[]> recentTop8  = productService.getRecentTopSold(8, id); // 현재 상품 제외
        boolean recentTopIsFallback = false;

        // 비었으면 누적 베스트로 대체
        if (recentTop8 == null || recentTop8.isEmpty()) {
            recentTop8 = productService.getAllTimeTopSold(8, id);
            recentTopIsFallback = true;
        }

        model.addAttribute("brandRecs", brandRecs);
        model.addAttribute("recentTop8", recentTop8);
        model.addAttribute("recentTopIsFallback", recentTopIsFallback);

        model.addAttribute("product", product);
        model.addAttribute("reviews", reviews);
        model.addAttribute("reviewCount", reviewCount);
        model.addAttribute("reviewAvg", avg);
        model.addAttribute("sort", sort);
        model.addAttribute("likedReviewIds", likedReviewIds);

        model.addAttribute("loggedIn", loggedIn);
        model.addAttribute("zzimedByMe", zzimedByMe);

        return "main/content2";
    }

    // 좋아요 토글
    @PostMapping("/etc/review/{reviewId}/like")
    @PreAuthorize("isAuthenticated()")
    @ResponseBody
    public Map<String, Object> toggleLike(@PathVariable("reviewId") Long reviewId,
            @AuthenticationPrincipal UserDetails principal) {
        Users actor = userRepository.findByUserName(principal.getUsername())
                .orElseThrow(() -> new IllegalStateException("사용자 정보를 찾을 수 없습니다."));
        boolean liked = reviewService.toggleLike(reviewId, actor);
        long count = reviewService.getLikeCount(reviewId);
        return Map.of("liked", liked, "count", count);
    }

    // 리뷰 작성 폼 (비로그인 접근 차단)
    @GetMapping("/etc/review/new")
    @PreAuthorize("isAuthenticated()")
    public String reviewNew(@RequestParam("productId") long productId,
            Model model) {
        Product product = productService.getProduct(productId);
        model.addAttribute("product", product);
        return "main/etc/review-form";
    }

    // 리뷰 저장 (인증 보장 + 널 안전)
    @PostMapping("/etc/review")
    @PreAuthorize("isAuthenticated()")
    public String reviewCreate(
            @RequestParam("productId") long productId,
            @RequestParam("rating") int rating,
            @RequestParam("content") String content,
            @AuthenticationPrincipal UserDetails principal,
            RedirectAttributes ra) {

        // 혹시 모를 널 방지 (필요 시 로그인으로 유도)
        if (principal == null) {
            return "redirect:/login?required&redirect=/main/content/" + productId;
        }

        Product product = productService.getProduct(productId);

        // 로그인 사용자 → Users 매핑
        Users writer = userRepository.findByUserName(principal.getUsername())
                .orElseThrow(() -> new IllegalStateException("사용자 정보를 찾을 수 없습니다."));

        Review saved = reviewService.write(product, writer, rating, content);

        ra.addFlashAttribute("msg", "후기가 등록되었습니다.");
        return "redirect:/main/content/" + product.getId();
    }

    // 리뷰 수정 폼
    @GetMapping("/etc/review/{reviewId}/edit")
    @PreAuthorize("isAuthenticated()")
    public String reviewEditForm(@PathVariable("reviewId") Long reviewId,
            @RequestParam("productId") long productId,
            @AuthenticationPrincipal UserDetails principal,
            Model model) {
        Users actor = userRepository.findByUserName(principal.getUsername())
                .orElseThrow(() -> new IllegalStateException("사용자 정보를 찾을 수 없습니다."));
        Review review = reviewService.get(reviewId); // 권한은 폼에서만 표시되지만 서버에서도 체크
        Product product = productService.getProduct(productId);

        model.addAttribute("product", product);
        model.addAttribute("review", review);
        return "main/etc/review-edit";
    }

    // 리뷰 수정 처리
    @PostMapping("/etc/review/{reviewId}/edit")
    @PreAuthorize("isAuthenticated()")
    public String reviewEdit(@PathVariable("reviewId") Long reviewId,
            @RequestParam("productId") long productId,
            @RequestParam("rating") int rating,
            @RequestParam("content") String content,
            @AuthenticationPrincipal UserDetails principal,
            RedirectAttributes ra) {
        Users actor = userRepository.findByUserName(principal.getUsername())
                .orElseThrow(() -> new IllegalStateException("사용자 정보를 찾을 수 없습니다."));
        reviewService.update(reviewId, actor, rating, content);
        ra.addFlashAttribute("msg", "후기가 수정되었습니다.");
        return "redirect:/main/content/" + productId;
    }

    // 리뷰 삭제
    @PostMapping("/etc/review/{reviewId}/delete")
    @PreAuthorize("isAuthenticated()")
    public String reviewDelete(@PathVariable("reviewId") Long reviewId,
            @RequestParam("productId") long productId,
            @AuthenticationPrincipal UserDetails principal,
            RedirectAttributes ra) {
        Users actor = userRepository.findByUserName(principal.getUsername())
                .orElseThrow(() -> new IllegalStateException("사용자 정보를 찾을 수 없습니다."));
        reviewService.delete(reviewId, actor);
        ra.addFlashAttribute("msg", "후기가 삭제되었습니다.");
        return "redirect:/main/content/" + productId;
    }

    // ✅ 리뷰 조각만 반환 (추천순/최신순 Ajax)
    @GetMapping("/content/{productId}/reviews")
    public String getReviewsFragment(
            @PathVariable("productId") long productId,
            @RequestParam(value = "sort", defaultValue = "best") String sort,
            @AuthenticationPrincipal UserDetails principal,
            Model model) {

        Product product = productService.getProduct(productId);

        // 기존과 동일한 소팅 로직 사용
        List<ReviewDto> reviews = reviewService.getReviews(product, sort);

        // 로그인 사용자가 누른 리뷰 id 세트
        Set<Long> likedReviewIds = new HashSet<>();
        if (principal != null) {
            String me = principal.getUsername();
            for (ReviewDto rv : reviews) {
                // ReviewDto.likers = List<String> (userName)
                if (rv.getLikers() != null && rv.getLikers().contains(me)) {
                    likedReviewIds.add(rv.getReviewId());
                }
            }
        }

        model.addAttribute("reviews", reviews);
        model.addAttribute("likedReviewIds", likedReviewIds);
        model.addAttribute("productId", productId);
        model.addAttribute("sort", sort);

        // content.html 안의 reviewList fragment 반환
        return "main/content2 :: reviewList";
    }

    // ========================= 리스트(정렬 + 페이징) =========================
    @GetMapping("/list")
    public String listPage(@RequestParam(name = "brandIds", required = false) List<Long> brandIds,
            @RequestParam(name = "gradeIds", required = false) List<Long> gradeIds,
            @RequestParam(name = "mainNoteIds", required = false) List<Long> mainNoteIds,
            @RequestParam(name = "volumeIds", required = false) List<Long> volumeIds,
            @RequestParam(name = "q", required = false) String keyword,
            @RequestParam(name = "sort", defaultValue = "id") String sort,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "24") int size,
            Model model) {

        // 옵션(필터)
        model.addAttribute("brands", productService.getBrandOptions());
        model.addAttribute("grades", productService.getGradeOptions());
        model.addAttribute("mainNotes", productService.getMainNoteOptions());
        model.addAttribute("volumes", productService.getVolumeOptions());

        // 리스트 + 전체 카운트
        Map<String, Object> res = productService.getProductsPaged(
                brandIds, gradeIds, mainNoteIds, volumeIds, keyword, sort, page, size);

        model.addAttribute("products", res.get("items"));
        model.addAttribute("total", res.get("total"));
        model.addAttribute("totalPages", res.get("totalPages"));
        model.addAttribute("page", res.get("page"));
        model.addAttribute("size", res.get("size"));

        model.addAttribute("keyword", keyword == null ? "" : keyword);
        model.addAttribute("sort", sort);

        return "main/list";
    }

    @GetMapping("/list/partial")
    public String listPartial(@RequestParam(name = "brandIds", required = false) List<Long> brandIds,
            @RequestParam(name = "gradeIds", required = false) List<Long> gradeIds,
            @RequestParam(name = "mainNoteIds", required = false) List<Long> mainNoteIds,
            @RequestParam(name = "volumeIds", required = false) List<Long> volumeIds,
            @RequestParam(name = "q", required = false) String keyword,
            @RequestParam(name = "sort", defaultValue = "id") String sort,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "24") int size,
            Model model) {

        Map<String, Object> res = productService.getProductsPaged(
                brandIds, gradeIds, mainNoteIds, volumeIds, keyword, sort, page, size);

        model.addAttribute("products", res.get("items"));
        model.addAttribute("total", res.get("total"));
        model.addAttribute("totalPages", res.get("totalPages"));
        model.addAttribute("page", res.get("page"));
        model.addAttribute("size", res.get("size"));
        model.addAttribute("keyword", keyword == null ? "" : keyword);
        model.addAttribute("sort", sort);

        return "main/list :: listBody";
    }

    // ===== 브랜드 목록 =====
    @GetMapping("/brand")
    public String brand(Model model) {
        List<Map<String, Object>> brands = productService.getBrands();
        model.addAttribute("brands", brands);
        return "main/brand";
    }

    // ===== 브랜드 상세 =====
    @GetMapping("/brand/{brandNo}")
    public String brandDetail(@PathVariable("brandNo") Long brandNo, Model model) {
        Map<String, Object> brand = productService.getBrand(brandNo);

        Map<String, Object> res = productService.getProducts(
                Collections.singletonList(brandNo),
                null, null, null,
                null);

        model.addAttribute("brand", brand);
        model.addAttribute("total", (Long) res.get("total"));
        model.addAttribute("products", (List<Map<String, Object>>) res.get("items"));

        return "main/brandDetail";
    }

    // ==== MY TYPE ====
    @GetMapping("/myType")
    public String myType() {
        return "main/myType";
    }

    /**
     * 설문 기반 향수 추천 API (간단 버전)
     */
    @GetMapping("/api/products/recommendations")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getRecommendations(
            @RequestParam("mainNoteIds") List<Long> mainNoteIds,
            @RequestParam(name = "size", defaultValue = "6") int size) {

        try {
            if (mainNoteIds == null || mainNoteIds.isEmpty()) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("message", "메인노트 ID가 필요합니다.");
                errorResponse.put("items", List.of());
                return ResponseEntity.badRequest().body(errorResponse);
            }

            // 기존 ProductService 메서드 활용
            List<Map<String, Object>> recommendations = productService.getRecommendationsByMainNotes(mainNoteIds, size);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("total", recommendations.size());
            response.put("items", recommendations);
            response.put("mainNoteIds", mainNoteIds);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            System.err.println("추천 API 오류: " + e.getMessage());
            e.printStackTrace();

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "추천 상품 조회 중 오류가 발생했습니다.");
            errorResponse.put("items", List.of());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * 설문 페이지용 - 메인노트별 상품 개수 조회 API
     * 
     * @param mainNoteIds 메인노트 ID들
     * @return 각 메인노트별 상품 개수 정보
     */
    @GetMapping("/api/products/count-by-mainnotes")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getProductCountByMainNotes(
            @RequestParam("mainNoteIds") List<Long> mainNoteIds) {

        try {
            if (mainNoteIds == null || mainNoteIds.isEmpty()) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("total", 0);
                errorResponse.put("message", "메인노트 ID가 필요합니다.");
                return ResponseEntity.badRequest().body(errorResponse);
            }

            long totalCount = productService.countProductsByMainNotes(mainNoteIds);
            boolean hasProducts = productService.hasRecommendableProducts(mainNoteIds);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("total", totalCount);
            response.put("mainNoteIds", mainNoteIds);
            response.put("hasRecommendableProducts", hasProducts);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            System.err.println("상품 개수 조회 API 오류: " + e.getMessage());

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("total", 0);
            errorResponse.put("error", e.getMessage());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    // ===================== 추가: 구매자 통계 API (명수) =====================
    @GetMapping("/api/product/{id}/buyer-stats")
    @ResponseBody
    public Map<String, Object> getBuyerStats(@PathVariable("id") long id) {
        return productService.getBuyerStatsForChart(id);
    }

    /**
     * 설문 결과 상세 분석 API (선택사항)
     * 
     * @param answers 설문 답변들 (JSON 형태)
     * @return 상세한 분석 결과 및 추천 이유
     */
    @PostMapping("/api/survey/analyze")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> analyzeSurveyResults(
            @RequestBody Map<String, String> answers) {

        try {
            // 설문 답변 분석 로직
            Map<String, Object> analysis = analyzeSurveyAnswers(answers);

            // 추천 메인노트 추출
            @SuppressWarnings("unchecked")
            List<Long> recommendedMainNotes = (List<Long>) analysis.get("recommendedMainNotes");

            // 해당 메인노트의 상품들 조회
            List<Map<String, Object>> products = productService.getRecommendationsByMainNotes(recommendedMainNotes, 6);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("analysis", analysis);
            response.put("recommendations", products);
            response.put("totalProducts", products.size());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            System.err.println("설문 분석 API 오류: " + e.getMessage());

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "설문 분석 중 오류가 발생했습니다.");

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * 설문 답변 분석 헬퍼 메서드
     * 
     * @param answers 설문 답변 Map
     * @return 분석 결과
     */
    private Map<String, Object> analyzeSurveyAnswers(Map<String, String> answers) {
        // 메인노트 매핑 (프론트엔드와 동일)
        Map<String, Long> mainNoteMapping = Map.of(
                "floral", 6L, // 플로랄
                "citrus", 7L, // 시트러스
                "woody", 2L, // 우디
                "spicy", 1L, // 스파이시
                "vanilla", 8L, // 바닐라
                "fruity", 4L, // 푸루티
                "herbal", 3L, // 허벌
                "gourmand", 5L // 구르망
        );

        String demographics = answers.get("demographics");
        String mood = answers.get("mood");
        String season = answers.get("season");
        String intensity = answers.get("intensity");
        String notes = answers.get("notes");
        String usage = answers.get("usage");
        String personality = answers.get("personality");
        String budget = answers.get("budget");

        // 메인 노트 결정
        Long primaryMainNote = mainNoteMapping.getOrDefault(notes, 6L); // 기본값: 플로랄
        Set<Long> secondaryMainNotes = new HashSet<>();

        // 답변 조합에 따른 추가 노트 추천
        if ("romantic".equals(mood)) {
            secondaryMainNotes.addAll(List.of(6L, 8L)); // 플로랄, 바닐라
        } else if ("professional".equals(mood)) {
            secondaryMainNotes.addAll(List.of(2L, 7L)); // 우디, 시트러스
        } else if ("casual".equals(mood)) {
            secondaryMainNotes.addAll(List.of(7L, 3L)); // 시트러스, 허벌
        } else if ("luxurious".equals(mood)) {
            secondaryMainNotes.addAll(List.of(1L, 5L)); // 스파이시, 구르망
        }

        if ("spring".equals(season)) {
            secondaryMainNotes.addAll(List.of(6L, 7L)); // 플로랄, 시트러스
        } else if ("summer".equals(season)) {
            secondaryMainNotes.addAll(List.of(7L, 3L)); // 시트러스, 허벌
        } else if ("autumn".equals(season)) {
            secondaryMainNotes.addAll(List.of(2L, 1L)); // 우디, 스파이시
        } else if ("winter".equals(season)) {
            secondaryMainNotes.addAll(List.of(8L, 5L)); // 바닐라, 구르망
        }

        if ("gentle".equals(personality)) {
            secondaryMainNotes.addAll(List.of(6L, 8L)); // 플로랄, 바닐라
        } else if ("confident".equals(personality)) {
            secondaryMainNotes.addAll(List.of(1L, 2L)); // 스파이시, 우디
        } else if ("fresh".equals(personality)) {
            secondaryMainNotes.addAll(List.of(7L, 3L)); // 시트러스, 허벌
        } else if ("mysterious".equals(personality)) {
            secondaryMainNotes.addAll(List.of(2L, 5L)); // 우디, 구르망
        }

        // 메인노트 추가 및 중복 제거, 최대 3개 선택
        secondaryMainNotes.add(primaryMainNote);
        List<Long> recommendedMainNotes = secondaryMainNotes.stream()
                .limit(3)
                .collect(Collectors.toList());

        // 분석 결과 구성
        Map<String, Object> analysis = new HashMap<>();
        analysis.put("primaryMainNote", primaryMainNote);
        analysis.put("recommendedMainNotes", recommendedMainNotes);
        analysis.put("userProfile", Map.of(
                "demographics", demographics,
                "mood", mood,
                "season", season,
                "intensity", intensity,
                "personality", personality,
                "budget", budget));

        // 추천 이유 생성
        String reason = generateRecommendationReason(answers, recommendedMainNotes);
        analysis.put("recommendationReason", reason);

        return analysis;
    }

    /**
     * 추천 이유 생성 헬퍼 메서드
     */
    private String generateRecommendationReason(Map<String, String> answers, List<Long> mainNoteIds) {
        Map<Long, String> mainNoteNames = Map.of(
                1L, "스파이시", 2L, "우디", 3L, "허벌", 4L, "푸루티",
                5L, "구르망", 6L, "플로랄", 7L, "시트러스", 8L, "바닐라");

        String mood = answers.get("mood");
        String personality = answers.get("personality");
        String season = answers.get("season");

        StringBuilder reason = new StringBuilder();
        reason.append("당신의 ");

        if ("romantic".equals(mood)) {
            reason.append("로맨틱한 감성");
        } else if ("professional".equals(mood)) {
            reason.append("프로페셔널한 매력");
        } else if ("casual".equals(mood)) {
            reason.append("자연스러운 일상");
        } else if ("luxurious".equals(mood)) {
            reason.append("럭셔리한 취향");
        }

        reason.append("과 ");

        if ("confident".equals(personality)) {
            reason.append("자신감 넘치는 개성");
        } else if ("gentle".equals(personality)) {
            reason.append("부드러운 매력");
        } else if ("fresh".equals(personality)) {
            reason.append("상쾌한 에너지");
        } else if ("mysterious".equals(personality)) {
            reason.append("신비로운 분위기");
        }

        reason.append("을 고려하여 ");

        List<String> noteNames = mainNoteIds.stream()
                .map(id -> mainNoteNames.getOrDefault(id, "특별한"))
                .collect(Collectors.toList());

        reason.append(String.join(", ", noteNames));
        reason.append(" 계열 향수를 추천드립니다.");

        return reason.toString();
    }
    
    /**
     * AI 개인화 향수 분석 API
     */
    @PostMapping("/ai/persona-recommendation")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getPersonaRecommendation(
            @RequestBody Map<String, Object> request) {
        
        try {
            Long productId = Long.valueOf(request.get("productId").toString());
            String gender = (String) request.get("gender");
            String ageGroup = (String) request.get("ageGroup");
            
            // 상품 정보 조회
            Product product = productService.getProduct(productId);
            
            // 프롬프트 생성
            String prompt = buildPersonaPrompt(product, gender, ageGroup);
            
            // AI 호출
            String recommendation = chatService.generatePersonaRecommendation(prompt);
            
            if (recommendation == null) {
                throw new RuntimeException("AI 분석 생성 실패");
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("recommendation", recommendation);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("개인화 추천 API 오류: {}", e.getMessage(), e);
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "분석 중 오류가 발생했습니다.");
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    // 프롬프트 생성 헬퍼 메서드(사용자가 선택했을 때)
    private String buildPersonaPrompt(Product product, String gender, String ageGroup) {
        String genderText = "M".equals(gender) ? "남성" : "여성";
        String ageText = switch(ageGroup) {
            case "10s" -> "10대";
            case "20s" -> "20대"; 
            case "30s" -> "30대";
            case "40s" -> "40대";
            case "50plus" -> "50대 이상";
            default -> "성인";
        };
        
        String cleanName = product.getName().replaceAll("\\s*\\d+\\s*[mM][lL]\\s*", "");
        
        return String.format("""
            %s %s이 %s(%s) 향수를 착용했을 때의 예상 시나리오를 분석해주세요:
            
            향수 정보:
            - 브랜드: %s
            - 제품: %s
            - 부향률: %s
            - 메인어코드: %s
            %s
            
            분석 관점:
            - 이 연령대/성별의 라이프스타일에 어떻게 어울릴지
            - 주변 사람들이 어떻게 인식할지
            - 어떤 상황에서 가장 매력적으로 느껴질지
            - 자신감이나 분위기에 어떤 영향을 줄지
            
            톤: 구체적이고 현실적인 예상, 긍정적이지만 과장되지 않게
            """, 
            ageText, genderText, cleanName, product.getBrand().getBrandName(),
            product.getBrand().getBrandName(), cleanName,
            product.getGrade().getGradeName(), product.getMainNote().getMainNoteName(),
            buildNoteInfo(product)
        );
    }

    private String buildNoteInfo(Product product) {
        StringBuilder notes = new StringBuilder();
        
        if (product.getSingleNote() != null && !product.getSingleNote().trim().isEmpty()) {
            notes.append("- 싱글노트: ").append(product.getSingleNote());
        } else {
            if (product.getTopNote() != null && !product.getTopNote().trim().isEmpty()) {
                notes.append("- 탑노트: ").append(product.getTopNote()).append("\n");
            }
            if (product.getMiddleNote() != null && !product.getMiddleNote().trim().isEmpty()) {
                notes.append("- 미들노트: ").append(product.getMiddleNote()).append("\n");
            }
            if (product.getBaseNote() != null && !product.getBaseNote().trim().isEmpty()) {
                notes.append("- 베이스노트: ").append(product.getBaseNote());
            }
        }
        
        return notes.toString();
    }
    // ==================================== 관심등록 ===================================
}
