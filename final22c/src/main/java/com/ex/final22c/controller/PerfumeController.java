package com.ex.final22c.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import com.ex.final22c.service.PerfumeService;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
@RequestMapping("/main/*")
public class PerfumeController {

    private PerfumeService perfumeService;
}
