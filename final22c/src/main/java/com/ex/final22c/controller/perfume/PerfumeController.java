package com.ex.final22c.controller.perfume;

import com.ex.final22c.data.perfume.Perfume;
import com.ex.final22c.service.perfume.PerfumeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequiredArgsConstructor
@RequestMapping("/main")
public class PerfumeController {

    private final PerfumeService perfumeService;

    // 목록 페이지: /main/list?q=&grades=...&accords=...
    @GetMapping("/list")
    public String list(@RequestParam(name = "q", required = false) String q,
                       @RequestParam(name = "grades", required = false) List<String> grades,
                       @RequestParam(name = "accords", required = false) List<String> accords,
                       Model model) {

        List<Perfume> list = perfumeService.search(q, grades, accords);
        model.addAttribute("list", list);
        model.addAttribute("q", q);
        model.addAttribute("grades", grades);
        model.addAttribute("accords", accords);

        return "main/list";
    }

    // 상세 페이지: /main/content/{perfumeNo}
    @GetMapping("/content/{perfumeNo}")
    public String perfumeContent(@PathVariable("perfumeNo") int perfumeNo,
                                 Model model) {
        Perfume perfume = perfumeService.getPerfume(perfumeNo);
        model.addAttribute("perfume", perfume);
        return "main/content";
    }
}
