package com.ex.final22c;

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
import org.springframework.stereotype.Component;

import com.ex.final22c.data.user.Users;
import com.ex.final22c.repository.user.UserRepository;
import com.ex.final22c.service.user.UsersSecurityService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class CustomAuthenticationProvider implements AuthenticationProvider {

    private final UsersSecurityService usersSecurityService;
    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String username = authentication.getName();
        String rawPw = String.valueOf(authentication.getCredentials());

        UserDetails userDetails = usersSecurityService.loadUserByUsername(username);

        //  비활성(탈퇴) 계정 차단
        Users u = userRepository.findByUserName(username)
                .orElseThrow(() -> new UsernameNotFoundException("사용자를 찾을 수 없습니다."));
        if ("inactive".equalsIgnoreCase(u.getStatus())) {
            throw new DisabledException("회원탈퇴 된 계정입니다.");
        }

        if (!passwordEncoder.matches(rawPw, userDetails.getPassword())) {
            throw new BadCredentialsException("아이디 또는 비밀번호가 올바르지 않습니다.");
        }

        return new UsernamePasswordAuthenticationToken(
                userDetails, rawPw, userDetails.getAuthorities());
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }
}