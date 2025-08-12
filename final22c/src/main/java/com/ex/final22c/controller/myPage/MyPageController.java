package com.ex.final22c.controller.myPage;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.ex.final22c.data.user.Users;
import com.ex.final22c.service.user.UserAddressService;
import com.ex.final22c.service.user.UsersService;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
@RequestMapping("/mypage")
public class MyPageController {

    /*
        마이페이지
        메인은 무엇으로 할것인가.
        1. 기본배송지 등록
        2. 활동내역(리뷰, 공감)
        3. 회원정보 수정
        4. 판매내역
        5. 관심(찜) 상품
        6. 주문내역
    */

    private final UsersService usersService;
    private final UserAddressService userAddressService;

    // 마이페이지 메인 화면
    @GetMapping("mypage")
    public String myPage() {
        return "/mypage/addresses";
    }

    // 현재 로그인한 회원의 배송지 목록 조회
    @GetMapping("/addresses")
    public String myAddresses(Model model, @AuthenticationPrincipal Users loginUser) {
        Long userNo = loginUser.getUserNo(); // Users 엔티티의 PK getter에 맞게 수정
        model.addAttribute("addresses", userAddressService.getMyAddresses(userNo));
        return "/mypage/addresses_list"; // Thymeleaf 뷰 파일명
    }
}