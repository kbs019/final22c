package com.ex.final22c.service.user;

import java.security.Principal;
import java.time.LocalDate;

import org.springframework.security.core.userdetails.UserDetails;
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

    // public Users getUser(String userName) {
    // Optional<Users> _user = userRepository.findByUserName(userName);
    // if (_user.isEmpty()) {
    // return null; // 사용자 없음
    // } else {
    // throw new DataNotFoundException("사용자를 찾을 수 없습니다.");
    // }
    // }

    // 사용자 정보 조회
    public Users getUser(String userName) {
        return userRepository.findByUserName(userName)
                .orElseThrow(() -> new DataNotFoundException("사용자를 찾을 수 없습니다."));
    }

    public boolean verifyPassword(String username, String raw, PasswordEncoder encoder) {
        Users u = getUser(username);
        return encoder.matches(raw, u.getPassword());
    }

    // 로그인 사용자 정보 조회
    public Users getLoginUser(Principal principal) {
        String username = principal.getName();
        return userRepository.findByUserName(username)
                .orElseThrow(() -> new UsernameNotFoundException("로그인 정보 없음"));
    }

    // 회원가입
    public Users create(UsersForm usersForm) {
        Users user = Users.builder()
                .userName(usersForm.getUserName())
                .password(passwordEncoder.encode(usersForm.getPassword1()))
                .email(usersForm.getEmail())
                .name(usersForm.getName())
                .birth(usersForm.getBirth())
                .telecom(usersForm.getTelecom())
                .phone(usersForm.getPhone())
                .gender(usersForm.getGender())
                .loginType("local")
                .mileage(0)
                .build();
        this.userRepository.save(user);
        return user;
    }

    // 이메일/휴대폰/비밀번호만 업데이트 ===
    public Users updateProfile(String username, String newEmail, String newPhone, String newPasswordNullable) {
        Users me = getUser(username);

        if (newEmail != null) {
            me.setEmail(newEmail.trim());
        }
        if (newPhone != null) {
            me.setPhone(newPhone.trim());
        }
        if (newPasswordNullable != null && !newPasswordNullable.isBlank()) {
            me.setPassword(passwordEncoder.encode(newPasswordNullable));
        }

        return userRepository.save(me);
    }
}