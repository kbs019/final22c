package com.ex.final22c.controller.myPage;

import java.security.Principal;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import com.ex.final22c.data.user.Users;
import com.ex.final22c.form.UsersAddressForm;
import com.ex.final22c.repository.mypage.UserAddressRepository;
import com.ex.final22c.service.mypage.UserAddressService;
import com.ex.final22c.service.user.UsersService;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
@RequestMapping("/mypage")
public class MyPageController {

    private final UsersService usersService;
    private final UserAddressRepository userAddressRepository;
    private final UserAddressService userAddressService;

    // ====== DTO ======
    public record AddressDto(
            Long addressNo,
            String addressName,
            String recipient,
            String phone,
            String zonecode,
            String roadAddress,
            String detailAddress,
            String isDefault
    ) {
        public static AddressDto from(com.ex.final22c.data.user.UserAddress ua) {
            return new AddressDto(
                    ua.getAddressNo(),
                    ua.getAddressName(),
                    ua.getRecipient(),
                    ua.getPhone(),
                    ua.getZonecode(),
                    ua.getRoadAddress(),
                    ua.getDetailAddress(),
                    ua.getIsDefault()
            );
        }
    }

    // ====== 주소지 페이지 ======
    @GetMapping("/address")
    public String addressPage(Model model, Principal principal) {
        Users me = usersService.getLoginUser(principal);
        model.addAttribute("userAddresses",
                userAddressRepository.findByUser_UserNo(me.getUserNo()));
        model.addAttribute("usersAddressForm", new UsersAddressForm());
        model.addAttribute("section", "address");
        return "myPage/addressForm";
    }

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

    @GetMapping(value = "/address/list", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> addressList(Principal principal) {
        Users me = usersService.getLoginUser(principal);
        var list = userAddressService.getUserAddressesList(me.getUserNo())
                .stream().map(AddressDto::from).toList();
        return ResponseEntity.ok(Map.of("addresses", list));
    }

    @PostMapping(value = "/address/default.json", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> setDefault(
            @RequestParam(value = "addressNo", required = false) Long addressNoForm,
            @RequestBody(required = false) Map<String, Long> body,
            Principal principal) {

        Long addressNo = addressNoForm != null ? addressNoForm
                : (body != null ? body.get("addressNo") : null);
        if (addressNo == null) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "message", "addressNo is required"));
        }

        Users me = usersService.getLoginUser(principal);
        userAddressService.setDefault(me.getUserNo(), addressNo);

        var selected = userAddressService.getUserAddressesList(me.getUserNo()).stream()
                .filter(a -> addressNo.equals(a.getAddressNo()))
                .findFirst().map(AddressDto::from).orElse(null);

        return ResponseEntity.ok(Map.of("ok", true, "address", selected));
    }

    @PostMapping(value = "/address/new", produces = "application/json")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> createAddressAjax(
            @Valid UsersAddressForm form,
            BindingResult br,
            Principal principal) {
        if (br.hasErrors()) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "message", "validation"));
        }
        Users me = usersService.getLoginUser(principal);
        var saved = userAddressService.insertUserAddressReturn(me.getUserNo(), form);
        return ResponseEntity.ok(Map.of("ok", true, "address", AddressDto.from(saved)));
    }

    // ====== 찜목록 ======
    @GetMapping("/wishlist")
    public String wishlistPage(Model model, Principal principal) {
        if (principal == null) return "redirect:/user/login";
        model.addAttribute("section", "wishlist");
        return "mypage/wishlist";
    }

    // ====== 프로필 보호(비번 확인) REST API - 정적 내부 클래스로 선언! ======
    @RestController
    @RequiredArgsConstructor
    @RequestMapping("/mypage/profile")
    public static class ProfileGuardController {

        private final UsersService usersService;
        private final PasswordEncoder passwordEncoder;

        @PostMapping("/verify")
        public ResponseEntity<?> verify(@RequestBody Map<String, String> req,
                                        Principal principal,
                                        HttpSession session) {
            if (principal == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("ok", false, "message", "로그인이 필요합니다."));
            }
            String raw = req.getOrDefault("password", "");
            Users me = usersService.getUser(principal.getName());
            if (!passwordEncoder.matches(raw, me.getPassword())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("ok", false, "message", "비밀번호가 올바르지 않습니다."));
            }
            session.setAttribute("PROFILE_AUTH_AT", System.currentTimeMillis()); // 5분 유효 등
            return ResponseEntity.ok(Map.of("ok", true));
        }
    }

    // ====== 프로필 화면 (동일 파일, 뷰 컨트롤러) ======
    @GetMapping("/profile")
    public String profileForm(Principal principal, HttpSession session, Model model) {
        if (principal == null) return "redirect:/user/login";

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
}
