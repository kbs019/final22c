package com.ex.final22c.controller.myPage;

import java.security.Principal;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

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

    // ====== DTO ======
    public record AddressDto(
            Long addressNo,
            String addressName,
            String recipient,
            String phone,
            String zonecode,
            String roadAddress,
            String detailAddress,
            String isDefault) {
        public static AddressDto from(com.ex.final22c.data.user.UserAddress ua) {
            return new AddressDto(
                    ua.getAddressNo(),
                    ua.getAddressName(),
                    ua.getRecipient(),
                    ua.getPhone(),
                    ua.getZonecode(),
                    ua.getRoadAddress(),
                    ua.getDetailAddress(),
                    ua.getIsDefault());
        }
    }

    // ====== 주소지 페이지 ======
    @GetMapping("/address")
    public String addressPage(Model model, Principal principal) {
        if (principal == null)
            return "redirect:/user/login";
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
        if (principal == null)
            return "redirect:/user/login";
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
        if (principal == null)
            return ResponseEntity.status(401).build();
        Long addressNo = payload.get("addressNo");
        if (addressNo == null)
            return ResponseEntity.badRequest().build();
        Users me = usersService.getLoginUser(principal);
        userAddressService.setDefault(me.getUserNo(), addressNo);
        return ResponseEntity.ok().build();
    }

    @GetMapping(value = "/address/list", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> addressList(Principal principal) {
        if (principal == null)
            return ResponseEntity.status(401).build();
        Users me = usersService.getLoginUser(principal);
        var list = userAddressService.getUserAddressesList(me.getUserNo())
                .stream().map(AddressDto::from).toList();
        return ResponseEntity.ok(Map.of("addresses", list));
    }

    @PostMapping(value = "/address/default.json", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> setDefaultJson(
            @RequestParam(value = "addressNo", required = false) Long addressNoForm,
            @RequestBody(required = false) Map<String, Long> body,
            Principal principal) {

        if (principal == null)
            return ResponseEntity.status(401).build();

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

    // ====== 찜목록 ======
    @GetMapping("/wishlist")
    public String wishlistPage(Model model, Principal principal) {
        if (principal == null)
            return "redirect:/user/login";
        model.addAttribute("section", "wishlist");
        return "mypage/wishlist";
    }

}
