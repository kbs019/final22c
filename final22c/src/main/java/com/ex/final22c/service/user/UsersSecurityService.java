package com.ex.final22c.service.user;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.ex.final22c.data.user.UserRole;
import com.ex.final22c.data.user.Users;
import com.ex.final22c.repository.user.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UsersSecurityService implements UserDetailsService {

    private final UserRepository usersRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Users u = usersRepository.findByUserName(username)
                .orElseThrow(() -> new UsernameNotFoundException("사용자를 찾을 수 없습니다."));

        String status = (u.getStatus() == null) ? "active" : u.getStatus().toLowerCase();
        Optional<Users> _users = this.usersRepository.findByUserName(username);
        // 1) 영구정지
        if ("banned".equals(status)) {
            throw new LockedException("영구정지된 회원입니다.");
        }

        // 2) 기간정지
        if ("suspended".equals(status)) {
            LocalDate until = resolveBanUntil(u.getBanReg());
            if (until != null && LocalDate.now().isBefore(until)) {
                throw new LockedException("정지된 회원입니다. 해제 예정: " + until);
            }
            // 기간이 지났으면 ACTIVE로 전환
            u.setStatus("active");
            u.setBanReg(null);
            usersRepository.save(u);
        }

        if(_users.isEmpty()) {
            throw new UsernameNotFoundException("사용자를 찾을 수 없습니다.");
         }
         Users users = _users.get();
         // 사용자 권한 객체
         List<GrantedAuthority> authorities = new ArrayList<>();
         if("admin".equals(username)) {
            // 아이디가 admin 이면 admin권한
            authorities.add(new SimpleGrantedAuthority(UserRole.ADMIN.getValue()));
         }else {
            // 아니면 user권한
            authorities.add(new SimpleGrantedAuthority(UserRole.USER.getValue()));
         }
         
         // 아이디,비밀번호,권한 리턴
         return new User(users.getUserName(),users.getPassword(),authorities);
      }

    /** BANREG을 LocalDate로 변환 */
    private LocalDate resolveBanUntil(Object banreg) {
        if (banreg == null) return null;

        if (banreg instanceof LocalDate d) return d;

        if (banreg instanceof java.util.Date d) {
            return d.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        }

        if (banreg instanceof String s) {
            s = s.trim();
            String[] patterns = { "yy/MM/dd", "yyyy-MM-dd", "yyyy/MM/dd" };
            for (String p : patterns) {
                try {
                    DateTimeFormatter f = DateTimeFormatter.ofPattern(p);
                    return LocalDate.parse(s, f);
                } catch (Exception ignore) {}
            }
        }

        // 파싱 실패 → 안전하게 지금보다 훨씬 미래 날짜로 리턴
        return LocalDate.now().plusYears(100);
    }
}
