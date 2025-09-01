package com.ex.final22c.service.user;

import java.security.Principal;
import java.util.Optional;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.ex.final22c.DataNotFoundException;
import com.ex.final22c.data.user.Users;
import com.ex.final22c.form.UsersForm;
import com.ex.final22c.repository.user.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UsersService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /** 사용자명으로 조회(없으면 DataNotFoundException) */
    public Users getUser(String userName) {
        return userRepository.findByUserName(userName)
                .orElseThrow(() -> new DataNotFoundException("사용자를 찾을 수 없습니다."));
    }

    /** 로그인 Principal로 조회(없으면 UsernameNotFoundException) */
    public Users getLoginUser(Principal principal) {
        String username = principal.getName();
        return userRepository.findByUserName(username)
                .orElseThrow(() -> new UsernameNotFoundException("로그인 정보 없음"));
    }

    /** 비밀번호 검증 */
    public boolean verifyPassword(String username, String raw) {
        Users u = getUser(username);
        return passwordEncoder.matches(raw, u.getPassword());
    }

    /** 회원가입 */
    public Users create(UsersForm usersForm) {
        Users user = Users.builder()
                .userName(usersForm.getUserName())
                .password(passwordEncoder.encode(usersForm.getPassword1()))
                .email(safeLowerTrim(usersForm.getEmail()))
                .name(usersForm.getName())
                .birth(usersForm.getBirth())
                .telecom(usersForm.getTelecom())
                .phone(safeTrim(usersForm.getPhone()))
                .gender(usersForm.getGender())
                .loginType("local")
                .mileage(0)
                .build();
        return userRepository.save(user);
    }

    /** 이메일/휴대폰/비밀번호만 업데이트 (null/blank는 미변경) + 본인 제외 중복 검사 */
    @Transactional
    public Users updateProfile(String username, String newEmail, String newPhone, String newPasswordNullable) {
        Users me = getUser(username);

        String emailNorm = isBlank(newEmail) ? null : safeLowerTrim(newEmail);

        // phone: 하이푼 제거
        String phoneNorm = isBlank(newPhone) ? null : newPhone.replaceAll("-", "").trim();

        // --- 중복 검사 ---
        if (emailNorm != null) {
            Optional<Users> existingEmail = userRepository.findByEmail(emailNorm);
            if (existingEmail.isPresent() && !existingEmail.get().getUserNo().equals(me.getUserNo())) {
                throw new IllegalArgumentException("이미 사용 중인 이메일입니다.");
            }
        }
        if (phoneNorm != null) {
            Optional<Users> existingPhone = userRepository.findByPhone(phoneNorm);
            if (existingPhone.isPresent() && !existingPhone.get().getUserNo().equals(me.getUserNo())) {
                throw new IllegalArgumentException("이미 사용 중인 휴대폰 번호입니다.");
            }
        }

        // --- 저장 ---
        if (emailNorm != null)
            me.setEmail(emailNorm);
        if (phoneNorm != null)
            me.setPhone(phoneNorm); // DB에는 01012345678 형식 저장
        if (!isBlank(newPasswordNullable)) {
            me.setPassword(passwordEncoder.encode(newPasswordNullable));
        }

        return me;
    }

    /** 전체 Users 객체 업데이트(중복 검사 포함) */
    @Transactional
    public Users updateUser(Users user) {
        // 이메일 중복 체크 (본인 제외)
        Optional<Users> existingEmail = userRepository.findByEmail(safeLowerTrim(user.getEmail()));
        if (existingEmail.isPresent() && !existingEmail.get().getUserNo().equals(user.getUserNo())) {
            throw new IllegalArgumentException("이미 사용 중인 이메일입니다.");
        }

        // 휴대폰 중복 체크 (본인 제외)
        Optional<Users> existingPhone = userRepository.findByPhone(safeTrim(user.getPhone()));
        if (existingPhone.isPresent() && !existingPhone.get().getUserNo().equals(user.getUserNo())) {
            throw new IllegalArgumentException("이미 사용 중인 휴대폰 번호입니다.");
        }

        // 정규화 후 저장
        user.setEmail(safeLowerTrim(user.getEmail()));
        user.setPhone(safeTrim(user.getPhone()));
        return userRepository.save(user);
    }

    // ---- helpers ----
    /** 빈 문자열 여부 확인 */
    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    /** null-세이프 trim */
    private static String safeTrim(String s) {
        return s == null ? null : s.trim();
    }

    /** null-세이프 trim + 소문자 변환(이메일 표준화용) */
    private static String safeLowerTrim(String s) {
        return s == null ? null : s.trim().toLowerCase();
    }

    /** 휴대폰/통신사만 변경: 입력은 하이푼 포함, DB엔 숫자만 저장. 본인 제외 중복검사 */
    @Transactional
    public Users updatePhoneAndTelecom(String username, String newPhone, String newTelecom) {
        Users me = getUser(username);

        // 1) 입력 정규화(뷰에서 하이푼 포함 전달)
        String phoneHyphen = (newPhone == null) ? "" : newPhone.trim();
        String telNorm = (newTelecom == null) ? "" : newTelecom.trim();

        if (phoneHyphen.isEmpty() || telNorm.isEmpty()) {
            throw new IllegalArgumentException("휴대번호/통신사를 확인해 주세요.");
        }

        // 2) 형식 검증 (입력은 하이푼 포함)
        if (!phoneHyphen.matches("^010-\\d{4}-\\d{4}$")) {
            throw new IllegalArgumentException("휴대폰 번호 형식을 확인해 주세요. (예: 010-1234-5678)");
        }

        // 3) DB 저장/중복검사는 숫자만으로 통일
        String phoneDigits = phoneHyphen.replaceAll("-", ""); // "01012345678"

        userRepository.findByPhone(phoneDigits).ifPresent(other -> {
            if (!other.getUserNo().equals(me.getUserNo())) {
                throw new IllegalArgumentException("이미 사용 중인 휴대폰 번호입니다.");
            }
        });

        // 4) 저장
        me.setPhone(phoneDigits); // DB에는 숫자만
        me.setTelecom(telNorm);
        return me;
    }

    /** 이메일 사용 가능 여부 사전 체크(본인 이메일이면 사용 가능으로 간주) */
    public boolean isEmailAvailableFor(String usernameNullable, String newEmail) {
        String emailNorm = safeLowerTrim(newEmail);
        if (emailNorm == null)
            return false;

        Users self = null;
        if (usernameNullable != null) {
            try {
                self = getUser(usernameNullable);
            } catch (Exception ignored) {
            }
        }

        // 본인 이메일이면 사용 가능으로 처리
        if (self != null && emailNorm.equals(safeLowerTrim(self.getEmail()))) {
            return true;
        }

        return userRepository.findByEmail(emailNorm).isEmpty();
    }
}