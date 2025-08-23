package com.ex.final22c;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
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
            .authorizeHttpRequests(auth -> auth
                // --- 정적 리소스/공개 페이지 허용 ---
                .requestMatchers(
                    "/", "/error", "/favicon.ico",
                    "/css/**", "/js/**", "/img/**", "/assets/**"
                ).permitAll()

                // --- 로그인/회원 관련 공개 경로 ---
                .requestMatchers(
                    "/user/login", "/user/signup", "/user/find/**"
                ).permitAll()

                // --- 리뷰 작성 관련: 인증 필요 ---
                .requestMatchers(HttpMethod.GET,  "/main/etc/review/new").authenticated()
                .requestMatchers(HttpMethod.POST, "/main/etc/review").authenticated()

                // --- 역할 보호 구간 ---
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .requestMatchers("/mypage/**").hasRole("USER")

                // --- 나머지 전체 허용(원래 설정 유지) ---
                .anyRequest().permitAll()
            )

            // CSRF 현재 비활성화(폼에서 _csrf 미사용 기준). 사용 전환 시 폼에 토큰 추가 필요.
            .csrf(csrf -> csrf.disable())

            // --- 폼 로그인 ---
            .formLogin(form -> form
                .loginPage("/user/login")
                .defaultSuccessUrl("/user/redirectByRole", true)
                .permitAll()
            )

            // --- 로그아웃 ---
            .logout(logout -> logout
                .logoutUrl("/user/logout")
                .logoutSuccessUrl("/main/list")
                .permitAll()
            );

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
