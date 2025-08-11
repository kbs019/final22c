package com.ex.final22c.service;

import java.time.LocalDate;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.ex.final22c.data.Users;
import com.ex.final22c.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UsersService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

        public Users create(String username, String password, String email) {
            Users user = Users.builder()
                .userName(username)
                .password(passwordEncoder.encode(password))
                .email(email)
                .reg(LocalDate.now())
                .status("active")
                .role("user")
                .build();
            this.userRepository.save(user);
            return user;
        }
}