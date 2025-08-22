package com.ex.final22c.controller.myPage;

import java.util.Map;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.ex.final22c.data.user.Users;
import com.ex.final22c.service.mypage.ProfileService;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/mypage/profile")
@RequiredArgsConstructor
public class MyProfileController {
    private final ProfileService profileService;

    // 비밀번호 확인 페이지
    @GetMapping("/check")
    public String checkPage(){ return "mypage/profile-check"; }

    // 비밀번호 검증(AJAX) → 통과 시 세션 플래그 세팅
    @PostMapping("/check")
    @ResponseBody
    public Map<String,Object> check(@AuthenticationPrincipal UserDetails me,
                                    @RequestParam String password,
                                    HttpSession session) {
        boolean ok = profileService.checkPassword(me.getUsername(), password);
        if (ok) session.setAttribute("PROFILE_VERIFIED", true);
        return Map.of("ok", ok);
    }

    // 내 정보 조회
    @GetMapping
    public String view(@AuthenticationPrincipal UserDetails me,
                       HttpSession session, Model model) {
        Boolean verified = (Boolean) session.getAttribute("PROFILE_VERIFIED");
        if (verified == null || !verified) return "redirect:/mypage/profile/check";
        Users u = profileService.getByUsername(me.getUsername());
        model.addAttribute("u", u);
        return "mypage/profile";
    }

    // 비밀번호 변경
    @PatchMapping("/password")
    @ResponseBody
    public Map<String,Object> changePassword(@AuthenticationPrincipal UserDetails me,
                                             @RequestParam String newPassword){
        profileService.changePassword(me.getUsername(), newPassword);
        return Map.of("ok", true);
    }

    // 이메일 중복 체크
    @GetMapping("/email/check")
    @ResponseBody
    public Map<String,Object> emailCheck(@RequestParam String email){
        return Map.of("usable", profileService.emailUsable(email));
    }

    // 이메일 변경
    @PatchMapping("/email")
    @ResponseBody
    public Map<String,Object> changeEmail(@AuthenticationPrincipal UserDetails me,
                                          @RequestParam String email){
        profileService.changeEmail(me.getUsername(), email);
        return Map.of("ok", true);
    }

    // 휴대폰 중복 체크
    @GetMapping("/phone/check")
    @ResponseBody
    public Map<String,Object> phoneCheck(@RequestParam String phone){
        return Map.of("usable", profileService.phoneUsable(phone));
    }

    // 휴대폰 변경
    @PatchMapping("/phone")
    @ResponseBody
    public Map<String,Object> changePhone(@AuthenticationPrincipal UserDetails me,
                                          @RequestParam String phone){
        profileService.changePhone(me.getUsername(), phone);
        return Map.of("ok", true);
    }
}
}
