package com.ex.final22c.controller.myPage;

import java.security.Principal;
import java.util.Map;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.ex.final22c.data.user.Users;
import com.ex.final22c.service.user.UsersService;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
@RequestMapping("/mypage/profile") // 프로필 전용 베이스 경로
public class MyProfileController {

    private final UsersService usersService;
    private final PasswordEncoder passwordEncoder;

    /** 프로필 페이지: 검증 상태와 사용자 정보 바인딩 */
    @GetMapping
    public String profilePage(Principal principal, HttpSession session, Model model) {
        if (principal == null) return "redirect:/user/login";

        Users me = usersService.getUser(principal.getName());
        Long ts = (Long) session.getAttribute("PROFILE_AUTH_AT");
        boolean verified = (ts != null) && (System.currentTimeMillis() - ts <= 5 * 60 * 1000L);

        model.addAttribute("section", "profile");
        model.addAttribute("verified", verified);
        model.addAttribute("me", me);
        return "mypage/profile"; // templates/mypage/profile.html
    }

    /**
     * 프로필 저장 (email/phone(전체 or 분할)/password 변경)
     * - 요청값이 비어 있으면 해당 항목은 미변경
     */
    @PostMapping(consumes = "application/x-www-form-urlencoded")
    public String saveProfile(
            Principal principal,
            HttpSession session,
            RedirectAttributes ra,
            @RequestParam(name = "email",    required = false) String email,   // 합쳐진 email
            @RequestParam(name = "phone",    required = false) String phone,   // hidden 전체값
            @RequestParam(name = "phone2",   required = false) String phone2,  // 분할값
            @RequestParam(name = "phone3",   required = false) String phone3,  // 분할값
            @RequestParam(name = "newPassword",     required = false) String newPassword,
            @RequestParam(name = "confirmPassword", required = false) String confirmPassword) {

        if (principal == null) return "redirect:/user/login";

        // 5분 재인증 가드
        Long ts = (Long) session.getAttribute("PROFILE_AUTH_AT");
        boolean verified = (ts != null) && (System.currentTimeMillis() - ts <= 5 * 60 * 1000L);
        if (!verified) {
            ra.addFlashAttribute("error", "재인증이 필요합니다.");
            return "redirect:/mypage/profile";
        }

        String trimmedEmail = (email == null || email.isBlank()) ? null : email.trim();

        // phone: 전체값 우선, 없으면 분할값 결합(010 고정)
        String newPhone = null;
        if (phone != null && !phone.isBlank()) {
            newPhone = phone.trim();
        } else if (phone2 != null || phone3 != null) {
            String p2 = phone2 == null ? "" : phone2.trim();
            String p3 = phone3 == null ? "" : phone3.trim();
            newPhone = "010-" + p2 + "-" + p3;
        }

        // 서버 검증
        if (trimmedEmail != null && !trimmedEmail.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            ra.addFlashAttribute("error", "이메일 형식을 확인해 주세요.");
            return "redirect:/mypage/profile";
        }
        if (newPhone != null && !newPhone.matches("^010-\\d{4}-\\d{4}$")) {
            ra.addFlashAttribute("error", "휴대폰 번호 형식을 확인해 주세요.");
            return "redirect:/mypage/profile";
        }

        String pwToSet = null;
        if (newPassword != null && !newPassword.isBlank()) {
            if (newPassword.length() < 8) {
                ra.addFlashAttribute("error", "새 비밀번호는 8자 이상이어야 합니다.");
                return "redirect:/mypage/profile";
            }
            if (confirmPassword == null || !newPassword.equals(confirmPassword)) {
                ra.addFlashAttribute("error", "새 비밀번호와 확인이 일치하지 않습니다.");
                return "redirect:/mypage/profile";
            }
            pwToSet = newPassword;
        }

        try {
            usersService.updateProfile(principal.getName(), trimmedEmail, newPhone, pwToSet);
            ra.addFlashAttribute("message", "개인정보가 성공적으로 수정되었습니다.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "수정 중 오류가 발생했습니다: " + e.getMessage());
        }

        return "redirect:/mypage/profile";
    }

    /** 프로필 보호: 비밀번호 재확인 (AJAX) */
    @PostMapping(value = "/verify", consumes = "application/json", produces = "application/json")
    @ResponseBody
    public ResponseEntity<?> verify(@RequestBody Map<String, String> req,
                                    Principal principal,
                                    HttpSession session) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("ok", false, "message", "로그인이 필요합니다."));
        }
        String raw = Optional.ofNullable(req.get("password")).orElse("");
        Users me = usersService.getUser(principal.getName());
        if (!passwordEncoder.matches(raw, me.getPassword())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("ok", false, "message", "비밀번호가 올바르지 않습니다."));
        }
        session.setAttribute("PROFILE_AUTH_AT", System.currentTimeMillis()); // 5분 유효
        return ResponseEntity.ok(Map.of("ok", true));
    }

    /** 비밀번호 일치 확인 (AJAX) */
    @PostMapping(value = "/pw-match", consumes = "application/json", produces = "application/json")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> passwordMatch(@RequestBody Map<String, String> req) {
        String pw1 = Optional.ofNullable(req.get("newPassword")).orElse("");
        String pw2 = Optional.ofNullable(req.get("confirmPassword")).orElse("");

        if (pw1.isBlank() && pw2.isBlank()) {
            return ResponseEntity.ok(Map.of("ok", false, "message", ""));
        }
        if (pw1.length() < 8) {
            return ResponseEntity.ok(Map.of("ok", false, "message", "비밀번호는 8자 이상이어야 합니다."));
        }

        boolean same = pw1.equals(pw2);
        return ResponseEntity.ok(Map.of(
                "ok", same,
                "message", same ? "비밀번호가 일치합니다." : "비밀번호가 일치하지 않습니다."));
    }

    /** 비밀번호만 변경 (AJAX) */
    @PostMapping(value = "/password", consumes = "application/json", produces = "application/json")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> changePassword(@RequestBody Map<String, String> req,
                                                              Principal principal,
                                                              HttpSession session) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("ok", false, "message", "로그인이 필요합니다."));
        }
        Long ts = (Long) session.getAttribute("PROFILE_AUTH_AT");
        boolean verified = (ts != null) && (System.currentTimeMillis() - ts <= 5 * 60 * 1000L);
        if (!verified) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("ok", false, "message", "재인증이 필요합니다."));
        }

        String pw1 = Optional.ofNullable(req.get("newPassword")).orElse("");
        String pw2 = Optional.ofNullable(req.get("confirmPassword")).orElse("");

        if (pw1.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "message", "새로운 비밀번호를 입력해 주세요."));
        }
        if (pw1.length() < 8) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "message", "비밀번호는 8자 이상이어야 합니다."));
        }
        if (!pw1.equals(pw2)) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "message", "비밀번호 확인이 일치하지 않습니다."));
        }

        usersService.updateProfile(principal.getName(), null, null, pw1);
        return ResponseEntity.ok(Map.of("ok", true, "message", "비밀번호가 변경되었습니다."));
    }

    /** 휴대폰 변경 (AJAX) */
    @PostMapping(value = "/phone", consumes = "application/json", produces = "application/json")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> changePhone(@RequestBody Map<String, String> body,
                                                           Principal principal,
                                                           HttpSession session) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("ok", false, "message", "로그인이 필요합니다."));
        }

        Long ts = (Long) session.getAttribute("PROFILE_AUTH_AT");
        boolean verified = (ts != null) && (System.currentTimeMillis() - ts <= 5 * 60 * 1000L);
        if (!verified) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("ok", false, "message", "재인증이 필요합니다."));
        }

        String phone   = Optional.ofNullable(body.get("phone")).orElse("").trim();
        String telecom = Optional.ofNullable(body.get("telecom")).orElse("").trim();

        if (!phone.matches("^010-\\d{4}-\\d{4}$") || telecom.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("ok", false, "message", "휴대번호/통신사를 확인해 주세요."));
        }

        try {
            usersService.updatePhoneAndTelecom(principal.getName(), phone, telecom);
            return ResponseEntity.ok(Map.of("ok", true, "message", "변경되었습니다."));
        } catch (IllegalArgumentException dup) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "message", dup.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("ok", false, "message", "변경 중 오류가 발생했습니다."));
        }
    }

    /** 이메일 중복 확인 (AJAX) */
    @PostMapping(value = "/email/check", consumes = "application/json", produces = "application/json")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> checkEmail(@RequestBody Map<String, String> body,
                                                          Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("ok", false, "message", "로그인이 필요합니다."));
        }

        String email = Optional.ofNullable(body.get("email")).orElse("").trim().toLowerCase();
        if (!email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            return ResponseEntity.badRequest()
                    .body(Map.of("ok", false, "message", "이메일 형식을 확인해 주세요."));
        }

        boolean available = usersService.isEmailAvailableFor(principal.getName(), email);
        return ResponseEntity.ok(Map.of(
                "ok", available,
                "message", available ? "사용 가능한 이메일입니다." : "중복된 이메일입니다."));
    }

    /** 이메일 실제 변경 (AJAX) */
    @PostMapping(value = "/email", consumes = "application/json", produces = "application/json")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> changeEmail(@RequestBody Map<String, String> body,
                                                           Principal principal,
                                                           HttpSession session) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("ok", false, "message", "로그인이 필요합니다."));
        }

        Long ts = (Long) session.getAttribute("PROFILE_AUTH_AT");
        boolean verified = (ts != null) && (System.currentTimeMillis() - ts <= 5 * 60 * 1000L);
        if (!verified) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("ok", false, "message", "재인증이 필요합니다."));
        }

        String email = Optional.ofNullable(body.get("email")).orElse("").trim().toLowerCase();
        if (!email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            return ResponseEntity.badRequest()
                    .body(Map.of("ok", false, "message", "이메일 형식을 확인해 주세요."));
        }

        try {
            usersService.updateProfile(principal.getName(), email, null, null);
            return ResponseEntity.ok(Map.of("ok", true, "message", "이메일이 변경되었습니다."));
        } catch (IllegalArgumentException dup) {
            return ResponseEntity.badRequest()
                    .body(Map.of("ok", false, "message", dup.getMessage())); // "이미 사용 중인 이메일입니다."
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("ok", false, "message", "변경 중 오류가 발생했습니다."));
        }
    }
}
