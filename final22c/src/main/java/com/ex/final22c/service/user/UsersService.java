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

        // 입력 정규화
        String emailNorm = isBlank(newEmail) ? null : safeLowerTrim(newEmail);
        String phoneNorm = isBlank(newPhone) ? null : safeTrim(newPhone);

        // 사전 중복 검사 (본인 제외)
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

        // 반영
        if (emailNorm != null)
            me.setEmail(emailNorm);
        if (phoneNorm != null)
            me.setPhone(phoneNorm);
        if (!isBlank(newPasswordNullable)) {
            me.setPassword(passwordEncoder.encode(newPasswordNullable));
        }

        // @Transactional + 영속 엔티티 변경 → flush 시점에 반영
        // 명시 저장을 원하면 아래 주석 해제
        // userRepository.save(me);

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
    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static String safeTrim(String s) {
        return s == null ? null : s.trim();
    }

    private static String safeLowerTrim(String s) {
        return s == null ? null : s.trim().toLowerCase();
    }
}
