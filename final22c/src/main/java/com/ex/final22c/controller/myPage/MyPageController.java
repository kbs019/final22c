package com.ex.final22c.controller.myPage;

import java.security.Principal;

import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.ex.final22c.data.user.Users;
import com.ex.final22c.form.UsersAddressForm;
import com.ex.final22c.service.mypage.UserAddressService;
import com.ex.final22c.service.user.UsersService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
@RequiredArgsConstructor
@RequestMapping("/mypage")
public class MyPageController {

    /*
        마이페이지
        메인은 무엇으로 할것인가.
        1. 기본배송지 등록
            controller: 작업중.
            service: 작업중.    (완?)
            repository: 작업중.
            entity: 완.
            form: 완.
            view: 작업중.

        2. 활동내역(리뷰, 공감)
        3. 회원정보 수정
        4. 판매내역
        5. 관심(찜) 상품
        6. 주문내역
    */

    private final UsersService usersService;
    private final UserAddressService userAddressService;

    // 마이페이지 메인 화면
    @GetMapping({"", "/"})
    public String myPage() {
        return "mypage/addresses";
    }

    @GetMapping("insertAddress")
    public String insertAddress( UsersAddressForm userAddressForm ) {
        return "addresses";
    }

    @PostMapping("insertAddress")
    public String postMethodName( @Valid UsersAddressForm usersAddressForm, BindingResult bindingResult, Principal principal) {
        
        if(bindingResult.hasErrors()) {
            return "addresses"; // 에러가 있을 경우 다시 입력 폼으로 이동
        }

        Users user = this.usersService.getUser(principal.getName());

        this.userAddressService.insertAddress(user.getUserNo(), usersAddressForm);

        return "redirect:/userAddress/addresses"; // 성공시 이동할 페이지
    }
}