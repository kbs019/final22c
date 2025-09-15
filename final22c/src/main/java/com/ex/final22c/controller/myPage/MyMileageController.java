package com.ex.final22c.controller.myPage;

import java.security.Principal;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.ex.final22c.data.user.MileageUsageDto;
import com.ex.final22c.service.mypage.MyMileageService;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
@RequestMapping("/mypage")
public class MyMileageController {
    private final MyMileageService myMileageService;


    @GetMapping("/mileage")
    public String mileage(
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "10") int size,
            Principal principal,
            Model model) {

        Pageable pageable = PageRequest.of(page - 1, size, Sort.by("regDate").descending());
        Page<MileageUsageDto> mileagePage = myMileageService.getMileageUsageWithBalance(principal.getName(), pageable);
        Integer mileage = myMileageService.getMileageByUserName(principal.getName());
        
        model.addAttribute("mileage", mileage);
        model.addAttribute("mileagePage", mileagePage);

        return "mypage/mileage";
    }

}