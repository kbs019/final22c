package com.ex.final22c.controller.main;

import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.ex.final22c.data.product.Product;
import com.ex.final22c.repository.orderDetail.OrderDetailRepository.ProductSalesView;
import com.ex.final22c.service.main.MainPageService;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class MainController {

    private final MainPageService mainPageService;

    @GetMapping({"/", "/main"})
    public String main(Model model) {
        // 배너(관리자 선택 이미지 경로) — 우선 하드코드/설정값로 주입
        model.addAttribute("bannerSrc", "/img/banner/main_banner01.jpg");

        // Pick: 캐러셀 전체 아이템은 넉넉히 가져오고 화면에서 4칸씩 넘김
        List<Product> picks = mainPageService.getPickedProducts(40);
        model.addAttribute("picks", picks);

        // 각 BEST 10개 (2행×5칸 느낌)
        List<ProductSalesView> bestAll   = mainPageService.getAllBest(10);
        List<ProductSalesView> bestWomen = mainPageService.getBestByGender("여자", 10);
        List<ProductSalesView> bestMen   = mainPageService.getBestByGender("남자", 10);

        model.addAttribute("bestAll", bestAll);
        model.addAttribute("bestWomen", bestWomen);
        model.addAttribute("bestMen", bestMen);

        return "main/main"; // templates/main/main.html
    }
}
