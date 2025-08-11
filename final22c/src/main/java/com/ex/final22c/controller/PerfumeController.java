package com.ex.final22c.controller;

import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.ex.final22c.data.Perfume;
import com.ex.final22c.service.PerfumeService;

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
        return "main/list";
    }
}
