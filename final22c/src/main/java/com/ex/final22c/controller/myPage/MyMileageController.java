package com.ex.final22c.controller.myPage;

import java.security.Principal;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.ex.final22c.data.user.Users;
import com.ex.final22c.repository.order.OrderRepository;
import com.ex.final22c.repository.user.UserRepository;
import com.ex.final22c.service.mypage.MyMileageService;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
@RequestMapping("/mypage")
public class MyMileageController {
    private final UserRepository usersRepository;
    private final MyMileageService myMileageService;

    @GetMapping("/mileage")
    public String mileage(
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "10") int size,
            Principal principal,
            Model model) {
        Users me = usersRepository.findByUserName(principal.getName())
                .orElseThrow(() -> new IllegalArgumentException("사용자 없음"));

        Page<OrderRepository.MileageRow> rows = myMileageService.getMileageHistory(me.getUserNo(), page, size);

        model.addAttribute("section", "mileage");
        model.addAttribute("me", me);
        model.addAttribute("rows", rows);

        return "mypage/mileage";
    }
}