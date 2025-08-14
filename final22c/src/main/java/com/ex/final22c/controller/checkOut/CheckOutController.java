package com.ex.final22c.controller.checkOut;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.ex.final22c.data.perfume.Perfume;
import com.ex.final22c.service.perfume.PerfumeService;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
@RequestMapping("/checkout")
public class CheckOutController {

    private final PerfumeService perfumeService;

    
/*   
    // 예: /checkout/order/5?qty=2 로 호출~
    @GetMapping("/order/{perfumeNo}")
    public String orderOne(@PathVariable("perfumeNo") int perfumeNo,
    					   @RequestParam(name="qty", defaultValue="1") int qty,
                           Model model) {

        Perfume perfume = perfumeService.getPerfume(perfumeNo);
        int unitPrice  = perfume.getSellPrice();
        int lineTotal  = unitPrice * qty;

        // 뷰에서 바로 쓰도록 기본 값들 세팅, 주문페이지에선 order에 넣을필요없음
        model.addAttribute("perfume", perfume);   // 기존 템플릿이 쓰는 객체
        model.addAttribute("qty", qty);
        model.addAttribute("unitPrice", unitPrice);
        model.addAttribute("lineTotal", lineTotal);

		/*
		 * // (선택) 카카오 Ready에 쓸 대표값 model.addAttribute("pgItemName",
		 * perfume.getPerfumeName()); model.addAttribute("pgTotalQty", qty);
		 * model.addAttribute("pgTotalAmount", lineTotal);
		 

        return "pay/checkout";
    }
*/	
    @PostMapping("/order/{perfumeNo}")
    public String orderOne(@RequestParam("perfumeNo") int perfumeNo, @RequestParam("quantity") int quantity, Model model) {
    	Perfume perfume = perfumeService.getPerfume(perfumeNo);
    	int unitPrice = perfume.getSellPrice();
    	int lineTotal = unitPrice * quantity;
    	
    	 // 뷰에서 바로 쓰도록 기본 값들 세팅, 주문페이지에선 order에 넣을필요없음
        model.addAttribute("perfume", perfume);   // 기존 템플릿이 쓰는 객체
        model.addAttribute("qty", quantity);
        model.addAttribute("unitPrice", unitPrice);
        model.addAttribute("lineTotal", lineTotal);
        
        return "pay/checkout";
    }
    
    
    
}