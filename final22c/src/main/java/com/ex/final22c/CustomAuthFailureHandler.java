package com.ex.final22c;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import com.ex.final22c.service.user.UsersSecurityService;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Component
public class CustomAuthFailureHandler implements AuthenticationFailureHandler {
    @Override
    public void onAuthenticationFailure(HttpServletRequest req,
                                        HttpServletResponse res,
                                        AuthenticationException ex)
            throws IOException, ServletException {

        String msg = "아이디 또는 비밀번호가 올바르지 않습니다.";

        // 1) 바로 LockedException 인가?
        if (ex instanceof org.springframework.security.authentication.LockedException) {
            msg = ex.getMessage();
        }
        // 2) DaoAuthenticationProvider가 래핑한 경우(cause가 LockedException)
        else if (ex.getCause() instanceof org.springframework.security.authentication.LockedException) {
            msg = ex.getCause().getMessage();
        }
        // 3) 그 외: 필요시 Disabled/BadCredentials 등 추가 분기 가능
        // else if (ex instanceof DisabledException) { ... }

        String encoded = java.net.URLEncoder.encode(msg, java.nio.charset.StandardCharsets.UTF_8);
        res.sendRedirect("/user/login?error=1&msg=" + encoded);
    }
}
