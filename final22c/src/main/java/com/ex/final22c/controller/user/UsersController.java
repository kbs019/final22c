package com.ex.final22c.controller.user;

import java.time.LocalDate;
import java.time.Period;
import java.util.Map;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.ex.final22c.form.UsersForm;
import com.ex.final22c.service.user.EmailVerifier;
import com.ex.final22c.service.user.UsersService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
@RequestMapping("/user")
public class UsersController {

    private final UsersService usersService;
    private final EmailVerifier emailVerifier;

    @GetMapping("/create")
    public String signupForm(UsersForm usersForm) {
        return "user/signupForm";
    }

    @PostMapping("/create")
    public String signup(@Valid UsersForm usersForm, BindingResult bindingResult) {
        if (bindingResult.hasErrors())
            return "user/signupForm";

        // 비밀번호 일치
        if (!usersForm.getPassword1().equals(usersForm.getPassword2())) {
            bindingResult.rejectValue("password2", "passwordInCorrect", "2개의 비밀번호가 일치하지 않습니다.");
            return "user/signupForm";
        }

        // 아이디 중복
        if (!usersService.isUserNameAvailable(usersForm.getUserName())) {
            bindingResult.rejectValue("userName", "duplicate", "이미 사용 중인 아이디입니다.");
            return "user/signupForm";
        }

        // 이메일 최종 정규화/검증
        String emailNorm = emailVerifier.normalize(usersForm.getEmail());
        if (!emailVerifier.isAcceptableForSignup(emailNorm)) {
            bindingResult.rejectValue("email", "invalidEmail", "사용할 수 없는 이메일입니다. 올바른 형식/도메인인지 확인해 주세요.");
            return "user/signupForm";
        }
        usersForm.setEmail(emailNorm);

        // === 휴대폰: 형식 → 중복 순으로 분리 체크 ===
        String phoneDigits = usersForm.getPhone() == null ? "" : usersForm.getPhone().replaceAll("\\D+", "");
        if (!phoneDigits.matches("^01[016789]\\d{8}$")) {
            bindingResult.rejectValue("phone", "invalidFormat", "휴대폰 번호 형식을 확인해 주세요.");
            return "user/signupForm";
        }
        // 중복
        if (usersService.existsPhone(phoneDigits)) { // 아래처럼 UsersService에 얇은 메서드 하나 추가
            bindingResult.rejectValue("phone", "duplicate", "이미 사용 중인 휴대폰 번호입니다.");
            return "user/signupForm";
        }
        // 폼에도 숫자만 세팅해 두면 이후 create()가 일관적으로 처리
        usersForm.setPhone(phoneDigits);

        // 생년월일 검증
        LocalDate birth = usersForm.getBirth();
        LocalDate today = LocalDate.now();
        if (birth == null) {
            bindingResult.rejectValue("birth", "birth.required", "생년월일을 입력해 주세요.");
            return "user/signupForm";
        }
        if (!birth.isBefore(today)) {
            bindingResult.rejectValue("birth", "birth.futureOrToday", "생년월일은 오늘 이전이어야 합니다.");
            return "user/signupForm";
        }
        if (java.time.Period.between(birth, today).getYears() < 8) {
            bindingResult.rejectValue("birth", "birth.minAge", "만 8세 이상만 가입할 수 있습니다.");
            return "user/signupForm";
        }

        usersService.create(usersForm); // create() 내부에서 PhoneCodeService.isVerified(...)로 최종 인증 확인 + 중복 재검증
        return "redirect:/user/login";
    }

    @GetMapping(value = "/check/password", produces = "application/json")
    @ResponseBody
    public Map<String, Object> checkPassword(@RequestParam("pw") String pw) {
        // 영문/숫자만 사용, 영문+숫자 각 1개 이상 포함, 총 8자 이상
        boolean valid = pw != null && pw.matches("^(?=.*[A-Za-z])(?=.*\\d)[A-Za-z\\d]{8,}$");
        String msg = valid ? "" : "영문+숫자 포함 8자 이상(영문/숫자만)";
        return Map.of("ok", true, "valid", valid, "msg", msg);
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
            return "redirect:/admin/dashboard";
        }
        return "redirect:/main";
    }

    @GetMapping(value = "/check/username", produces = "application/json")
    @ResponseBody
    public Map<String, Object> checkUsername(@RequestParam("userName") String userName) {
        boolean available = usersService.isUserNameAvailable(userName);
        return Map.of("ok", true, "available", available);
    }

    // ===== 강화된 이메일 체크 API =====
    @GetMapping(value = "/check/email", produces = "application/json")
    @ResponseBody
    public Map<String, Object> checkEmail(@RequestParam("email") String email) {
        String norm = emailVerifier.normalize(email);
        boolean syntax = emailVerifier.isSyntaxValid(norm);

        boolean mx = false;
        boolean disposable = false;
        boolean acceptable = false;

        if (syntax) {
            String domain = norm.substring(norm.lastIndexOf('@') + 1);
            disposable = emailVerifier.isDisposable(domain);
            mx = emailVerifier.hasMxOrA(domain);
            acceptable = syntax && !disposable && mx;
        }

        boolean available = acceptable && usersService.isEmailAvailable(norm);

        return Map.of(
                "ok", true,
                "normalized", norm,
                "syntax", syntax,
                "mx", mx,
                "disposable", disposable,
                "acceptable", acceptable, // 정책 통과 여부(중복 제외)
                "available", available // 최종 가입 가능한지(중복 포함)
        );
    }

    @GetMapping(value = "/check/phone", produces = "application/json")
    @ResponseBody
    public Map<String, Object> checkPhone(@RequestParam("phone") String phone) {
        boolean available = usersService.isPhoneAvailable(phone); // 화면에서 010-****-**** 형태여도 OK
        return Map.of("ok", true, "available", available);
    }
    
    @GetMapping("find")
    public String find() {
    	return "user/find";
    }
    
    // 아이디 찾기
    @PostMapping("findId")
    public String findId(@RequestParam("name") String name,@RequestParam("email") String email, Model model) {
        String userId = usersService.findId(name, email);
        model.addAttribute("resultType", "id");
        model.addAttribute("result", userId);
        return "user/findResult"; // 결과 페이지
    }
}