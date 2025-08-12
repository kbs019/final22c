package com.ex.final22c.controller.myPage;

import java.security.Principal;

import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.ex.final22c.data.user.Users;
import com.ex.final22c.form.UsersAddressForm;
import com.ex.final22c.service.mypage.UserAddressService;
import com.ex.final22c.service.user.UsersService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;


@Controller
@RequiredArgsConstructor
@RequestMapping("/mypage")
public class MyPageController {
    private final UsersService userService;
    private final UserAddressService userAddressService;

    @GetMapping({"", "/"})
    public String myPageAddress( UsersAddressForm usersAddressForm ) {
        return "myPage/addressForm";
    }

    @PostMapping({"", "/"})
    public String myPageAddress(    @Valid UsersAddressForm usersAddressForm, 
                                    BindingResult bindingResult, Principal principal ) {
        if (bindingResult.hasErrors()) {
            return "myPage/addressForm";
        }
        Users user = this.userService.getUser(principal.getName());

        this.userAddressService.insertUserAddress( user.getUserNo(), usersAddressForm );

        return "redirect:/mypage";  // 주소 목록 페이지로 리다이렉트
    }
}