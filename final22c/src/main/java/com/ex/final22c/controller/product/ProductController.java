package com.ex.final22c.controller.product;

import java.util.List;

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

    // 목록 페이지: /main/list?q=&grades=...&accords=...
    @GetMapping("/list")
    public String list(@RequestParam(name = "q", required = false) String q,
                    @RequestParam(name = "grades", required = false) List<String> grades,
                    @RequestParam(name = "accords", required = false) List<String> accords,
                    @RequestParam(name = "brands", required = false) List<String> brands,
                    @RequestParam(name = "volumes", required = false) List<String> volumes,
                    Model model) {

        // ★ 5-인자 서비스 호출 (브랜드/용량 반영)
        List<Product> list = productService.search(q, grades, accords, brands, volumes);

        model.addAttribute("list", list);

        model.addAttribute("q", q);
        model.addAttribute("grades", grades);
        model.addAttribute("accords", accords);
        model.addAttribute("brands", brands);
        model.addAttribute("volumes", volumes);

        return "main/list";
    }

    // 상세 페이지: /main/content/{perfumeNo}
    @GetMapping("/content/{id}")
    public String perfumeContent(@PathVariable("id") long id,
                                    Model model) {
        Product product = productService.getProduct(id);
        model.addAttribute("product", product);
        return "main/content";
    }
}
