package com.ex.final22c.controller.myPage;

import java.security.Principal;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.ui.Model;

import com.ex.final22c.data.user.Users;
import com.ex.final22c.form.UsersAddressForm;
import com.ex.final22c.repository.mypage.UserAddressRepository;
import com.ex.final22c.service.mypage.UserAddressService;
import com.ex.final22c.service.user.UsersService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
@RequestMapping("/mypage")
public class MyPageController {

    private final UsersService usersService;
    private final UserAddressRepository userAddressRepository;
    private final UserAddressService userAddressService;

    /* 같은 페이지 진입 (목록 + 모달 버튼) */
    @GetMapping("/address")
    public String addressPage(Model model, Principal principal) {
        Users me = usersService.getLoginUser(principal);
        model.addAttribute("userAddresses",
                userAddressRepository.findByUser_UserNo(me.getUserNo()));
        model.addAttribute("usersAddressForm", new UsersAddressForm());
        return "myPage/addressForm";
    }

    /* 모달에서 제출 → 저장 후 같은 페이지로 리다이렉트(PRG) */
    @PostMapping("/address")
    public String createAddress(@Valid UsersAddressForm form,
                                BindingResult br,
                                Principal principal,
                                Model model) {
        if (br.hasErrors()) {
            Users me = usersService.getLoginUser(principal);
            model.addAttribute("userAddresses",
                    userAddressRepository.findByUser_UserNo(me.getUserNo()));
            return "myPage/addressForm";
        }
        Users me = usersService.getLoginUser(principal);
        userAddressService.insertUserAddress(me.getUserNo(), form);
        return "redirect:/mypage/address";
    }

    @PostMapping("/address/default")
    @ResponseBody
    public ResponseEntity<Void> setDefault(@RequestBody Map<String, Long> payload,
                                        Principal principal) {
        Long addressNo = payload.get("addressNo");
        if (addressNo == null) return ResponseEntity.badRequest().build();

        Users me = usersService.getLoginUser(principal);
        userAddressService.setDefault(me.getUserNo(), addressNo);
        return ResponseEntity.ok().build();
    }
}
