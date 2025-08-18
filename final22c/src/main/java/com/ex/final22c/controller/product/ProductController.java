package com.ex.final22c.controller.product;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.ex.final22c.data.product.Product;
import com.ex.final22c.service.product.ProductService;

import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/main")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @GetMapping("/list")
    public String listPage(@RequestParam(name = "brandIds",    required = false) List<Long> brandIds,
                           @RequestParam(name = "gradeIds",    required = false) List<Long> gradeIds,
                           @RequestParam(name = "mainNoteIds", required = false) List<Long> mainNoteIds,
                           @RequestParam(name = "volumeIds",   required = false) List<Long> volumeIds,
                           @RequestParam(name = "q",           required = false) String keyword,
                           Model model) {

        // 옵션(좌측 필터 UI용) 유지
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

    @GetMapping("/content/{id}")
    public String productContent(@PathVariable("id") long id, Model model) {
        Product product = productService.getProduct(id);
        model.addAttribute("product", product);
        return "main/content";
    }

    // ===== 브랜드 목록 =====
    @GetMapping("/brand")
    public String brand(Model model) {
        List<Map<String, Object>> brands = productService.getBrands();
        model.addAttribute("brands", brands);
        return "main/brand";
    }

    // ===== 브랜드 상세(= 리스트 카드 + 브랜드 필터만 적용) =====
    @GetMapping("/brand/{brandNo}")
    public String brandDetail(@PathVariable("brandNo") Long brandNo, Model model) {
        Map<String, Object> brand = productService.getBrand(brandNo);

        Map<String, Object> res = productService.getProducts(
                Collections.singletonList(brandNo), // brandIds
                null, null, null,                   // grade/mainNote/volume
                null                                // keyword
        );

        model.addAttribute("brand", brand);
        model.addAttribute("total",  (Long) res.get("total"));
        model.addAttribute("products", (List<Map<String, Object>>) res.get("items"));

        return "main/brandDetail";
    }
}
