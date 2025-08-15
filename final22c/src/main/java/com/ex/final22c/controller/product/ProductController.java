package com.ex.final22c.controller.product;

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
                           Model model) {

        model.addAttribute("brands",    productService.getBrandOptions());
        model.addAttribute("grades",    productService.getGradeOptions());
        model.addAttribute("mainNotes", productService.getMainNoteOptions());
        model.addAttribute("volumes",   productService.getVolumeOptions());

        Map<String, Object> res = productService.getProducts(brandIds, gradeIds, mainNoteIds, volumeIds);
        model.addAttribute("products", res.get("items"));
        model.addAttribute("total",    res.get("total"));
        return "main/list";
    }

    @GetMapping("/list/partial")
    public String listPartial(@RequestParam(name = "brandIds",    required = false) List<Long> brandIds,
                              @RequestParam(name = "gradeIds",    required = false) List<Long> gradeIds,
                              @RequestParam(name = "mainNoteIds", required = false) List<Long> mainNoteIds,
                              @RequestParam(name = "volumeIds",   required = false) List<Long> volumeIds,
                              Model model) {
        Map<String, Object> res = productService.getProducts(brandIds, gradeIds, mainNoteIds, volumeIds);
        model.addAttribute("products", res.get("items"));
        model.addAttribute("total",    res.get("total"));
        return "main/list :: listBody";
    }

    // 상세 페이지: /main/content/{id}
    @GetMapping("/content/{id}")
    public String productContent(@PathVariable("id") long id, Model model) {
        Product product = productService.getProduct(id);
        model.addAttribute("product", product);
        return "main/content";
    }
}
