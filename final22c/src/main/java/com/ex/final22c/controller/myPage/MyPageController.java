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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

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
    
    // 주문페이지 뷰에서 필요한 정보만 가져오기 위해서 만듬
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

    /* 같은 페이지 진입 (목록 + 모달 버튼) */
    @GetMapping("/address")
    public String addressPage(Model model, Principal principal) {
        Users me = usersService.getLoginUser(principal);
        model.addAttribute("userAddresses",
        userAddressRepository.findByUser_UserNo(me.getUserNo()));
        model.addAttribute("usersAddressForm", new UsersAddressForm());
        model.addAttribute("section", "address");
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
    
    /* 주소 목록 JSON - 모달이 열릴 때 호출 */
    @GetMapping(value = "/address/list", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> addressList(Principal principal) {
        Users me = usersService.getLoginUser(principal);
        var list = userAddressService.getUserAddressesList(me.getUserNo())
                .stream().map(AddressDto::from).toList();
        return ResponseEntity.ok(Map.of("addresses", list));
    }

    /* 기본 배송지 설정 + 선택 주소 JSON 반환 (폼 or JSON 둘 다 지원) */
    @PostMapping(value = "/address/default", produces = MediaType.APPLICATION_JSON_VALUE)
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

        // 방금 선택한 주소를 찾아서 내려줌 (주문페이지 즉시 갱신 용도)
        var selected = userAddressService.getUserAddressesList(me.getUserNo()).stream()
                .filter(a -> addressNo.equals(a.getAddressNo()))
                .findFirst().map(AddressDto::from).orElse(null);

        return ResponseEntity.ok(Map.of("ok", true, "address", selected));
    }
    
    @PostMapping(value = "/address/new", produces = "application/json")
    @ResponseBody
    public ResponseEntity<Map<String,Object>> createAddressAjax(
    		@Valid UsersAddressForm form,
    		BindingResult br,
    		Principal principal){
    	if(br.hasErrors()) {
    		return ResponseEntity.badRequest().body(Map.of("ok", false, "message", "validation"));
    	}
    	 Users me = usersService.getLoginUser(principal);
    	    var saved = userAddressService.insertUserAddressReturn(me.getUserNo(), form);

    	    // saved가 기본값 Y로 저장됐더라도, 서비스에서 clear 처리했으니 일관됨
    	    return ResponseEntity.ok(Map.of(
    	            "ok", true,
    	            "address", AddressDto.from(saved)
    	    ));
    }

	// 찜목록
	@GetMapping("wishlist")
	public String wishlistPage(Model model, Principal principal){
		if (principal == null) return "redirect:/user/login";
		model.addAttribute("section", "wishlist");
		return "mypage/wishlist";
	}	
	@RestController
	@RequiredArgsConstructor
	@RequestMapping("/mypage/profile")
	public class ProfileGuardController {
		private final UsersService usersService;
		private final PasswordEncoder passwordEncoder; // 네 설정에 맞게

		@PostMapping("/verify")
		public ResponseEntity<?> verify(@RequestBody Map<String,String> req, Principal principal, HttpSession session) {
			if (principal == null) {
				return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("ok", false, "message", "로그인이 필요합니다."));
			}
			String raw = req.getOrDefault("password", "");
			Users me = usersService.getUser(principal.getName());
			if (!passwordEncoder.matches(raw, me.getPassword())) {
				return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("ok", false, "message", "비밀번호가 올바르지 않습니다."));
			}
			session.setAttribute("PROFILE_AUTH_AT", System.currentTimeMillis()); // 5분 유효 같은 만료는 접근 시 체크
			return ResponseEntity.ok(Map.of("ok", true));
		}
	}

	@Controller
	@RequiredArgsConstructor
	@RequestMapping("/mypage")
	class ProfileController {
		private final UsersService usersService;

		@GetMapping("/profile")
		public String profileForm(Principal principal, HttpSession session, Model model){
			if (principal == null) return "redirect:/user/login";

			Long ts = (Long) session.getAttribute("PROFILE_AUTH_AT");
			long now = System.currentTimeMillis();
			if (ts == null || (now - ts) > 5 * 60 * 1000L) { // 5분 유효 예시
				// 인증 만료 → 다시 비번 확인 유도
				model.addAttribute("section","profile");
				model.addAttribute("error","보안을 위해 다시 인증해 주세요.");
				return "redirect:/mypage/order"; // 또는 안내 페이지
			}

			Users me = usersService.getUser(principal.getName());
			model.addAttribute("me", me);
			model.addAttribute("section","profile");
			return "mypage/profileForm"; // 실제 폼 뷰
		}
	}
}
