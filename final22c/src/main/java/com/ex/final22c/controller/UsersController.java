package com.ex.final22c.controller;

import com.ex.final22c.form.UsersForm;
import com.ex.final22c.service.UsersService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

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
        // 사용자 이름, 이메일 중복 검사            
        try {
            this.usersService.create(usersForm.getUserName(), usersForm.getEmail(), usersForm.getPassword1());
        } catch(DataIntegrityViolationException e) {    // 중복된 사용자 이름 또는 이메일로 인해 예외 발생
            e.printStackTrace();
            bindingResult.reject("signupFailed", "이미 존재하는 사용자 이름 또는 이메일입니다.");
            return "user/signupForm";
        } catch (Exception e) { // 다른 예외 발생 시
            e.printStackTrace();
            bindingResult.reject("signupFailed", "회원 가입에 실패했습니다. 다시 시도해주세요.");
            return "user/signupForm";
        }
        // 사용자 생성 성공
        usersService.create(usersForm.getUserName(), usersForm.getPassword1(), usersForm.getEmail());
        
        // 회원 가입 성공 시 홈으로 리다이렉트
        return "redirect:/user/login";
    }

    @GetMapping("/login")
    public String login() {
        return "user/loginForm";
    }
}