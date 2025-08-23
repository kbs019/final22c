package com.ex.final22c.controller.product;

import java.util.Collections;
import java.util.List;
import java.util.Map;

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

    // 상세
    @GetMapping("/content/{id}")
    public String productContent(
            @PathVariable("id") long id,
            @RequestParam(value = "sort", defaultValue = "best") String sort,
            Model model) {

        Product product = productService.getProduct(id);

        long reviewCount = reviewService.count(product);
        double avg = reviewService.avg(product);

        model.addAttribute("product", product);
        model.addAttribute("reviews", reviewService.getReviews(product, sort));
        model.addAttribute("reviewCount", reviewCount);
        model.addAttribute("reviewAvg", avg);
        model.addAttribute("sort", sort);

        return "main/content";
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
}
