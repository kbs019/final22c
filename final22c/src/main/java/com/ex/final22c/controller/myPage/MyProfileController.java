package com.ex.final22c.controller.myPage;

import java.security.Principal;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.ex.final22c.data.user.Users;
import com.ex.final22c.service.user.UsersService;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
@RequestMapping("/profile")
public class MyProfileController {
    private final UsersService usersService;

    @GetMapping("/profile")
    public String profileForm(Principal principal, HttpSession session, Model model){
        if (principal == null) return "redirect:/user/login";

        long now = System.currentTimeMillis();
        Long ts = (Long) session.getAttribute("PROFILE_AUTH_AT");
        boolean verified = (ts != null) && (now - ts <= 5 * 60 * 1000L);

        model.addAttribute("section","profile");   // ← 사이드박스 활성
        model.addAttribute("verified", verified);  // ← 템플릿 분기용

        if (verified) {
            Users me = usersService.getUser(principal.getName());
            model.addAttribute("me", me);
        }
        return "mypage/profile"; // 위 템플릿 파일명
    }
}
