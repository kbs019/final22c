package com.ex.final22c;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests((auth) -> auth
                .requestMatchers("/user/create", "/user/login").permitAll()
                // 모든 접근 요청에 대해 인증을 요구
                .anyRequest().authenticated()
            )
            .formLogin((form) -> form
                .loginPage("/user/login")
                // 로그인 성공 후 이동할 URL 설정
                .defaultSuccessUrl("/")
            )
            .logout((logout) -> logout
                .logoutUrl("/user/logout")
                // 로그아웃 성공 후 이동할 URL 설정
                .logoutSuccessUrl("/")
            );
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
