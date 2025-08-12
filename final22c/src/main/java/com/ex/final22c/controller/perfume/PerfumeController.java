package com.ex.final22c.controller.perfume;

import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import com.ex.final22c.data.perfume.Perfume;
import com.ex.final22c.service.perfume.PerfumeService;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
@RequestMapping("/main/*")
public class PerfumeController {

    private final PerfumeService perfumeService;

    @GetMapping("list")
    public String home( Model model ){
        List<Perfume> list = perfumeService.showList();

        model.addAttribute("list", list);
        return "main/list1";
    }

    // 상세 페이지로 이동
    @GetMapping("content/{perfumeNo}")  // perfumeNo 를 URI 에 포함시켜 전송
    public String perfumeContent( @PathVariable("perfumeNo") int perfumeNo, Model model ){
        Perfume perfume = this.perfumeService.getPerfume(perfumeNo);

        model.addAttribute("perfume", perfume);

        return "main/content";
    }
}
