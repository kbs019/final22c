package com.ex.final22c;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;

import com.ex.final22c.service.user.UsersSecurityService;

import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final UsersSecurityService usersSecurityService;
    private final AuthenticationFailureHandler failureHandler; // ★ CustomAuthFailureHandler 주입
    private final CustomAuthenticationProvider customAuthenticationProvider; // ★ CustomAuthenticationProvider 주입

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                // --- 정적 리소스/공개 페이지 허용 ---
                .requestMatchers("/", "/error", "/favicon.ico",
                        "/css/**", "/js/**", "/img/**", "/assets/**").permitAll()

                // --- 로그인/회원 관련 공개 경로 ---
                .requestMatchers("/user/login", "/user/signup", "/user/find/**").permitAll()

                // --- 리뷰 작성 관련: 인증 필요 ---
                .requestMatchers(HttpMethod.GET, "/main/etc/review/new").authenticated()
                .requestMatchers(HttpMethod.POST, "/main/etc/review").authenticated()

                // --- 역할 보호 구간 ---
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .requestMatchers("/mypage/**").hasRole("USER")

                // --- 그 외 ---
                .anyRequest().permitAll()
            )
            .csrf(csrf -> csrf.disable())

            // --- ★ 폼 로그인 설정 ---
            .formLogin(form -> form
                .loginPage("/user/login") // 로그인 페이지 URL
                .loginProcessingUrl("/user/login") // ★ 폼 action과 일치해야 함
                .failureHandler(failureHandler) // ★ 정지/영구정지 메시지 전달
                .defaultSuccessUrl("/user/redirectByRole", true)
                .permitAll()
            )

            // --- 로그아웃 ---
            .logout(logout -> logout
                .logoutUrl("/user/logout")
                .logoutSuccessUrl("/main")
                .permitAll()
            )

            // --- ★ CustomAuthenticationProvider 등록 ---
            .authenticationProvider(customAuthenticationProvider);

        return http.build();
    }


}