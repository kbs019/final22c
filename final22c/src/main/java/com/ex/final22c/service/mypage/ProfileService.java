package com.ex.final22c.service.mypage;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ex.final22c.data.user.Users;
import com.ex.final22c.repository.user.UserRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProfileService {
    private final UserRepository usersRepository;
    private final PasswordEncoder passwordEncoder;

    public Users getByUsername(String username){
        return usersRepository.findByUserName(username)
                .orElseThrow(() -> new IllegalArgumentException("user not found"));
    }

    public boolean checkPassword(String username, String raw){
        Users u = getByUsername(username);
        return passwordEncoder.matches(raw, u.getPassword());
    }

    @Transactional
    public void changePassword(String username, String newRaw){
        Users u = getByUsername(username);
        u.setPassword(passwordEncoder.encode(newRaw));
    }

    public boolean emailUsable(String email){
        return !usersRepository.existsByEmail(email);
    }
    public boolean phoneUsable(String phone){
        return !usersRepository.existsByPhone(phone);
    }

    @Transactional
    public void changeEmail(String username, String email){
        Users u = getByUsername(username);
        u.setEmail(email);
    }

    @Transactional
    public void changePhone(String username, String phone){
        Users u = getByUsername(username);
        u.setPhone(phone);
    }
}
