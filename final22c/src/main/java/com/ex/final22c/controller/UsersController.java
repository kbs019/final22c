package com.ex.final22c.controller;

import com.ex.final22c.form.UsersForm;
import com.ex.final22c.service.UsersService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
        return "signupForm";
    }

    @PostMapping("/create")
    public String signup(@Valid UsersForm usersForm, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return "signupForm";
        }
        if (!usersForm.getPassword1().equals(usersForm.getPassword2())) {
            bindingResult.rejectValue("password2", "passwordInCorrect",
                    "2개의 비밀번호가 일치하지 않습니다.");
            return "signupForm";
        }
        usersService.create(usersForm.getUserName(),
                            usersForm.getPassword1(),
                            usersForm.getEmail());
        return "redirect:/";
    }
}