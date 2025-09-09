package com.ex.final22c;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class CustomAuthFailureHandler implements AuthenticationFailureHandler {
    
    @Override
    public void onAuthenticationFailure(HttpServletRequest req,
            HttpServletResponse res,
            AuthenticationException ex)
            throws IOException, ServletException {

        String msg = "아이디 또는 비밀번호가 올바르지 않습니다.";

        if (ex instanceof org.springframework.security.authentication.LockedException) {
            msg = ex.getMessage();
        } else if (ex.getCause() instanceof org.springframework.security.authentication.LockedException) {
            msg = ex.getCause().getMessage();
        }
        // ⬇️ 추가: 비활성(탈퇴) 계정
        else if (ex instanceof org.springframework.security.authentication.DisabledException) {
            msg = ex.getMessage(); // "회원탈퇴 된 계정입니다. 새로 가입해 주세요."
        } else if (ex.getCause() instanceof org.springframework.security.authentication.DisabledException) {
            msg = ex.getCause().getMessage();
        }

        String encoded = URLEncoder.encode(msg, StandardCharsets.UTF_8);
        res.sendRedirect("/user/login?error=1&msg=" + encoded);
    }
}
