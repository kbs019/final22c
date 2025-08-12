package com.ex.final22c.controller.user;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.ex.final22c.form.UsersForm;
import com.ex.final22c.service.user.UsersService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
@RequestMapping("/user")
public class UsersController {

    private final UsersService usersService;

    @GetMapping("/create")
    public String signupForm(UsersForm usersForm) {
        return "user/signupForm";
    }

    @PostMapping("/create")
    public String signup(@Valid UsersForm usersForm, BindingResult bindingResult) {
        // 유효성 검사 결과가 있다면 다시 폼으로 돌아감
        if (bindingResult.hasErrors()) {
            return "user/signupForm";
        }
        // 비밀번호 일치 여부 검사
        if (!usersForm.getPassword1().equals(usersForm.getPassword2())) {
            bindingResult.rejectValue("password2", "passwordInCorrect", "2개의 비밀번호가 일치하지 않습니다.");
            return "user/signupForm";
        }

        
        // 사용자 생성 성공
        usersService.create(usersForm);
        
        // 회원 가입 성공 시 홈으로 리다이렉트
        return "redirect:/user/login";
    }

    @GetMapping("/login")
    public String login() {
        return "user/loginForm";
    }
    
    @GetMapping("/redirectByRole")
    public String redirectByRole(Authentication authentication) {
        // 현재 로그인 사용자의 첫 번째(그리고 유일한) 권한 가져오기
        String role = authentication.getAuthorities().iterator().next().getAuthority();

        if ("ROLE_ADMIN".equals(role)) {
            return "redirect:/admin/userList";
        }
        return "redirect:/main/list";
    }
}