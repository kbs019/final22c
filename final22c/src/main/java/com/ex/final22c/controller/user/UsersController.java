package com.ex.final22c.controller.user;

import java.time.LocalDate;
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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.ex.final22c.form.UsersForm;
import com.ex.final22c.service.user.EmailVerifier;
import com.ex.final22c.service.user.UsersService;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
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
        if (bindingResult.hasErrors()) return "user/signupForm";

        if (!usersForm.getPassword1().equals(usersForm.getPassword2())) {
            bindingResult.rejectValue("password2", "passwordInCorrect", "2개의 비밀번호가 일치하지 않습니다.");
            return "user/signupForm";
        }

        if (!usersService.isUserNameAvailable(usersForm.getUserName())) {
            bindingResult.rejectValue("userName", "duplicate", "이미 사용 중인 아이디입니다.");
            return "user/signupForm";
        }

        String emailNorm = emailVerifier.normalize(usersForm.getEmail());
        if (!emailVerifier.isAcceptableForSignup(emailNorm)) {
            bindingResult.rejectValue("email", "invalidEmail", "사용할 수 없는 이메일입니다. 올바른 형식/도메인인지 확인해 주세요.");
            return "user/signupForm";
        }
        usersForm.setEmail(emailNorm);

        String phoneDigits = usersForm.getPhone() == null ? "" : usersForm.getPhone().replaceAll("\\D+", "");
        if (!phoneDigits.matches("^01[016789]\\d{8}$")) {
            bindingResult.rejectValue("phone", "invalidFormat", "휴대폰 번호 형식을 확인해 주세요.");
            return "user/signupForm";
        }
        if (usersService.existsPhone(phoneDigits)) {
            bindingResult.rejectValue("phone", "duplicate", "이미 사용 중인 휴대폰 번호입니다.");
            return "user/signupForm";
        }
        usersForm.setPhone(phoneDigits);

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

        usersService.create(usersForm);
        return "redirect:/user/login?joined=1";
    }

    @GetMapping(value = "/check/password", produces = "application/json")
    @ResponseBody
    public Map<String, Object> checkPassword(@RequestParam("pw") String pw) {
        boolean valid = pw != null
                && pw.matches("^(?=.*[A-Za-z])(?=.*\\d)(?=.*[!@#$%^&*()_\\-+={}\\[\\]|\\\\:;'\"<>,.?/~`]).{8,150}$");
        String msg = valid ? "" : "영문+숫자+특수문자 포함 8자 이상";
        return Map.of("ok", true, "valid", valid, "msg", msg);
    }

    @GetMapping("/login")
    public String login() { return "user/loginForm"; }

    @GetMapping("/redirectByRole")
    public String redirectByRole(Authentication authentication) {
        String role = authentication.getAuthorities().iterator().next().getAuthority();
        if ("ROLE_ADMIN".equals(role)) return "redirect:/admin/dashboard";
        return "redirect:/main";
    }

    @GetMapping(value = "/check/username", produces = "application/json")
    @ResponseBody
    public Map<String, Object> checkUsername(@RequestParam("userName") String userName) {
        boolean available = usersService.isUserNameAvailable(userName);
        return Map.of("ok", true, "available", available);
    }

    @GetMapping(value = "/check/email", produces = "application/json")
    @ResponseBody
    public Map<String, Object> checkEmail(@RequestParam("email") String email) {
        String norm = emailVerifier.normalize(email);
        boolean syntax = emailVerifier.isSyntaxValid(norm);

        boolean mx = false, disposable = false, acceptable = false;
        if (syntax) {
            String domain = norm.substring(norm.lastIndexOf('@') + 1);
            disposable = emailVerifier.isDisposable(domain);
            mx = emailVerifier.hasMxOrA(domain);
            acceptable = syntax && !disposable && mx;
        }
        boolean available = acceptable && usersService.isEmailAvailable(norm);
        return Map.of("ok", true, "normalized", norm, "syntax", syntax, "mx", mx,
                "disposable", disposable, "acceptable", acceptable, "available", available);
    }

    @GetMapping(value = "/check/phone", produces = "application/json")
    @ResponseBody
    public Map<String, Object> checkPhone(@RequestParam("phone") String phone) {
        boolean available = usersService.isPhoneAvailable(phone);
        return Map.of("ok", true, "available", available);
    }

    @GetMapping("find")
    public String find() { return "user/find"; }

    // 아이디 찾기(JSON)
    @PostMapping(value = "/findId", produces = "application/json")
    @ResponseBody
    public Map<String, Object> findIdJson(
            @RequestParam("name") String name,
            @RequestParam("email") String email) {

        String userId = usersService.findId(name, email);
        if (userId != null && !userId.isBlank()) {
            return Map.of("success", true, "userName", userId);
        }
        return Map.of("success", false, "message", "일치하는 회원 정보를 찾을 수 없습니다.");
    }

    // Step 1: 아이디 + 이메일 → 인증코드 발송
    @PostMapping(value = "/findPw", produces = "application/json")
    @ResponseBody
    public Map<String, Object> sendAuthCode(@RequestParam("userName") String userName,
                                            @RequestParam("email") String email,
                                            HttpSession session) {
        try {
            boolean ok = usersService.sendResetPasswordAuthCode(userName, email);
            if (ok) {
                session.setAttribute("resetUserName", userName);
                return Map.of("success", true);
            }
            return Map.of("success", false, "message", "아이디와 이메일이 일치하지 않습니다.");
        } catch (Exception e) {
            log.error("findPw error: userName={}, email={}", userName, email, e);
            return Map.of("success", false, "message", "비밀번호 재설정 메일 발송 중 오류가 발생했습니다.");
        }
    }

    // Step 2: 인증번호 검증
    @PostMapping(value = "/verifyAuthCode", produces = "application/json")
    @ResponseBody
    public Map<String, Object> verifyAuthCode(@RequestParam("authCode") String authCode,
                                              HttpSession session) {
        String userName = (String) session.getAttribute("resetUserName");
        if (userName == null) {
            return Map.of("success", false, "message", "세션이 만료되었습니다. 처음부터 다시 진행해 주세요.");
        }
        boolean ok = usersService.verifyAuthCode(userName, authCode);
        return ok ? Map.of("success", true)
                  : Map.of("success", false, "message", "인증번호가 올바르지 않거나 만료되었습니다.");
    }

    // Step 3: 새 비밀번호 화면
    @GetMapping("/resetPwForm")
    public String resetPwForm(HttpSession session, Model model) {
        String userName = (String) session.getAttribute("resetUserName");
        model.addAttribute("userName", userName);
        return "user/resetPwForm";
    }

    // Step 4: 새 비밀번호 저장
    @PostMapping("/resetPw")
    public String resetPw(@RequestParam("userName") String userName,
                        @RequestParam("newPassword") String newPassword,
                        @RequestParam(value = "confirmPassword", required = false) String confirmPassword,
                        RedirectAttributes redirectAttributes) {

        // 회원가입과 동일한 서버 규칙
        boolean valid = newPassword != null &&
                newPassword.matches("^(?=.*[A-Za-z])(?=.*\\d)(?=.*[!@#$%^&*()_\\-+={}\\[\\]|\\\\:;'\"<>,.?/~`]).{8,150}$");

        if (!valid) {
            redirectAttributes.addFlashAttribute("error", "영문+숫자+특수문자 포함 8자 이상이어야 합니다.");
            return "redirect:/user/resetPwForm";
        }
        if (confirmPassword != null && !newPassword.equals(confirmPassword)) {
            redirectAttributes.addFlashAttribute("error", "비밀번호 확인이 일치하지 않습니다.");
            return "redirect:/user/resetPwForm";
        }

        usersService.resetPassword(userName, newPassword);
        redirectAttributes.addFlashAttribute("msg", "비밀번호가 성공적으로 변경되었습니다.");
        return "redirect:/user/login?reset=1";
    }

    @PostMapping(value = "/resetPwAjax", produces = "application/json")
    @ResponseBody
    public Map<String, Object> resetPwAjax(@RequestParam("userName") String userName,
                                        @RequestParam("newPassword") String newPassword,
                                        @RequestParam(value = "confirmPassword", required = false) String confirmPassword) {

        boolean valid = newPassword != null &&
                newPassword.matches("^(?=.*[A-Za-z])(?=.*\\d)(?=.*[!@#$%^&*()_\\-+={}\\[\\]|\\\\:;'\"<>,.?/~`]).{8,150}$");
        if (!valid) {
            return Map.of("success", false, "message", "영문+숫자+특수문자 포함 8자 이상이어야 합니다.");
        }
        if (confirmPassword != null && !newPassword.equals(confirmPassword)) {
            return Map.of("success", false, "message", "비밀번호 확인이 일치하지 않습니다.");
        }

        usersService.resetPassword(userName, newPassword);
        return Map.of("success", true);
    }
}
