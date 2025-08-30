package com.ex.final22c.controller.myPage;

import java.security.Principal;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

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
    public String profileForm(Principal principal, HttpSession session, Model model) {
        if (principal == null)
            return "redirect:/user/login";

        long now = System.currentTimeMillis();
        Long ts = (Long) session.getAttribute("PROFILE_AUTH_AT");
        boolean verified = (ts != null) && (now - ts <= 5 * 60 * 1000L);

        model.addAttribute("section", "profile");
        model.addAttribute("verified", verified);

        if (verified) {
            Users me = usersService.getUser(principal.getName());
            model.addAttribute("me", me);
        }
        return "mypage/profile";
    }

    // === 여기 추가: 폼 저장 ===
    // 폼에서 "보내는 값"을 명시:
    // - email
    // - phone2 (가운데 4자리)
    // - phone3 (끝 4자리)
    // - newPassword
    // - confirmPassword
    @PostMapping(path = "/profile", consumes = "application/x-www-form-urlencoded")
    public String saveProfile(
            Principal principal,
            HttpSession session,
            @RequestParam(name = "email", required = false) String email,
            @RequestParam(name = "phone2", required = false) String phone2,
            @RequestParam(name = "phone3", required = false) String phone3,
            @RequestParam(name = "newPassword", required = false) String newPassword,
            @RequestParam(name = "confirmPassword", required = false) String confirmPassword,
            Model model) {
        if (principal == null)
            return "redirect:/user/login";

        // 1) 가드(5분 인증) 확인
        Long ts = (Long) session.getAttribute("PROFILE_AUTH_AT");
        boolean verified = (ts != null) && (System.currentTimeMillis() - ts <= 5 * 60 * 1000L);
        if (!verified) {
            // 인증 만료 → 다시 GET 화면으로
            model.addAttribute("section", "profile");
            model.addAttribute("verified", false);
            return "mypage/profile";
        }

        // 2) 기본 검증 (서버단)
        if (email != null && !email.isBlank()) {
            if (!email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
                // 이메일 형식 오류 → 다시 폼
                Users me = usersService.getUser(principal.getName());
                model.addAttribute("section", "profile");
                model.addAttribute("verified", true);
                model.addAttribute("me", me);
                model.addAttribute("error", "이메일 형식을 확인해 주세요.");
                return "mypage/profile";
            }
        }

        // 휴대폰 조합 (첫번째는 010 고정)
        String newPhone = null;
        if (phone2 != null || phone3 != null) {
            String p2 = phone2 == null ? "" : phone2.trim();
            String p3 = phone3 == null ? "" : phone3.trim();
            newPhone = "010-" + p2 + "-" + p3;

            if (!newPhone.matches("^010-\\d{4}-\\d{4}$")) {
                Users me = usersService.getUser(principal.getName());
                model.addAttribute("section", "profile");
                model.addAttribute("verified", true);
                model.addAttribute("me", me);
                model.addAttribute("error", "휴대폰 번호 형식을 확인해 주세요.");
                return "mypage/profile";
            }
        }

        // 비밀번호(선택)
        String pwToSet = null;
        if (newPassword != null && !newPassword.isBlank()) {
            if (newPassword.length() < 8) {
                Users me = usersService.getUser(principal.getName());
                model.addAttribute("section", "profile");
                model.addAttribute("verified", true);
                model.addAttribute("me", me);
                model.addAttribute("error", "새 비밀번호는 8자 이상이어야 합니다.");
                return "mypage/profile";
            }
            if (confirmPassword == null || !newPassword.equals(confirmPassword)) {
                Users me = usersService.getUser(principal.getName());
                model.addAttribute("section", "profile");
                model.addAttribute("verified", true);
                model.addAttribute("me", me);
                model.addAttribute("error", "새 비밀번호와 확인이 일치하지 않습니다.");
                return "mypage/profile";
            }
            pwToSet = newPassword;
        }

        // 3) 업데이트 수행 (이메일/휴대폰/비밀번호만)
        usersService.updateProfile(
                principal.getName(),
                email, // null이면 변경 안 함
                newPhone, // null이면 변경 안 함
                pwToSet // null이면 변경 안 함
        );

        // 4) 성공 후 화면 재로딩
        return "redirect:/profile/profile?ok=1";
    }
}
