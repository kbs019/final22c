package com.ex.final22c.controller;

import org.springframework.stereotype.Controller;

import com.ex.final22c.service.PerfumeService;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class PerfumeController {

    private PerfumeService perfumeService;
}
