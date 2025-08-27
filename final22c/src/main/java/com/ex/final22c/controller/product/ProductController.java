package com.ex.final22c.controller.product;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.ex.final22c.data.product.Product;
import com.ex.final22c.data.product.Review;
import com.ex.final22c.data.user.Users;
import com.ex.final22c.repository.user.UserRepository;
import com.ex.final22c.service.product.ProductService;
import com.ex.final22c.service.product.ReviewService;

import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/main")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;
    private final ReviewService reviewService;
    private final UserRepository userRepository;

    @GetMapping("")
    public String main(Model model) {

        // 배너(이미지 경로는 필요 시 변경)
        model.addAttribute("bannerUrl", "/img/banner/main-banner.jpg");

        // PICK 캐러셀(4개씩)
        model.addAttribute("pickSlides", productService.getPickSlides(4));

        // 전체 베스트 TOP10
        model.addAttribute("allBest", productService.getAllBest(10));

        // 여성/남성 베스트 TOP10 (Users.gender: 1=남, 2=여)
        model.addAttribute("womanBest", productService.getGenderBest("F", 10));  // ✅ 여성 = F
        model.addAttribute("manBest",   productService.getGenderBest("M", 10));  // ✅ 남성 = M

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
        List<Review> reviews = reviewService.getReviews(product, sort);

        // 로그인 사용자가 누른 리뷰 id 세트(템플릿에서 상태 표시용)
        Set<Long> likedReviewIds = new HashSet<>();
        if (principal != null) {
            String me = principal.getUsername();
            for (Review rv : reviews) {
                if (rv.getLikers().stream().anyMatch(u -> me.equals(u.getUserName()))) {
                    likedReviewIds.add(rv.getReviewId());
                }
            }
        }

        model.addAttribute("product", product);
        model.addAttribute("reviews", reviews);
        model.addAttribute("reviewCount", reviewCount);
        model.addAttribute("reviewAvg", avg);
        model.addAttribute("sort", sort);
        model.addAttribute("likedReviewIds", likedReviewIds);

        return "main/content";
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
        // 소유/관리자 여부 서버에서 한 번 걸러두는 편
        // (폼 진입 자체를 제한하고 싶으시면 reviewService.update의 authorize 로직을 재사용)
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
        // service.delete에서 productId를 반환하도록 만들었지만 명시적 파라미터로도 받습니다.
        reviewService.delete(reviewId, actor);
        ra.addFlashAttribute("msg", "후기가 삭제되었습니다.");
        return "redirect:/main/content/" + productId;
    }

    // 리스트
    @GetMapping("/list")
    public String listPage(@RequestParam(name = "brandIds",    required = false) List<Long> brandIds,
                           @RequestParam(name = "gradeIds",    required = false) List<Long> gradeIds,
                           @RequestParam(name = "mainNoteIds", required = false) List<Long> mainNoteIds,
                           @RequestParam(name = "volumeIds",   required = false) List<Long> volumeIds,
                           @RequestParam(name = "q",           required = false) String keyword,
                           Model model) {

        model.addAttribute("brands",    productService.getBrandOptions());
        model.addAttribute("grades",    productService.getGradeOptions());
        model.addAttribute("mainNotes", productService.getMainNoteOptions());
        model.addAttribute("volumes",   productService.getVolumeOptions());

        Map<String, Object> res = productService.getProducts(brandIds, gradeIds, mainNoteIds, volumeIds, keyword);
        model.addAttribute("products", res.get("items"));
        model.addAttribute("total",    res.get("total"));
        model.addAttribute("keyword",  keyword == null ? "" : keyword);
        return "main/list";
    }

    @GetMapping("/list/partial")
    public String listPartial(@RequestParam(name = "brandIds",    required = false) List<Long> brandIds,
                              @RequestParam(name = "gradeIds",    required = false) List<Long> gradeIds,
                              @RequestParam(name = "mainNoteIds", required = false) List<Long> mainNoteIds,
                              @RequestParam(name = "volumeIds",   required = false) List<Long> volumeIds,
                              @RequestParam(name = "q",           required = false) String keyword,
                              Model model) {
        Map<String, Object> res = productService.getProducts(brandIds, gradeIds, mainNoteIds, volumeIds, keyword);
        model.addAttribute("products", res.get("items"));
        model.addAttribute("total",    res.get("total"));
        model.addAttribute("keyword",  keyword == null ? "" : keyword);
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
                null
        );

        model.addAttribute("brand", brand);
        model.addAttribute("total",  (Long) res.get("total"));
        model.addAttribute("products", (List<Map<String, Object>>) res.get("items"));

        return "main/brandDetail";
    }
    
    // ==== MY TYPE ====
    @GetMapping("/myType")
    public String myType() {
    	return "main/myType";
    }
}
