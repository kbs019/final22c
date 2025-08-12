package com.ex.final22c.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.ex.final22c.data.UserRole;
import com.ex.final22c.data.Users;
import com.ex.final22c.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Service
public class UsersSecurityService implements UserDetailsService {
	private final UserRepository usersRepository;
	
	public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
		Optional<Users> _users = this.usersRepository.findByUserName(username);
		
		if(_users.isEmpty()) {
			throw new UsernameNotFoundException("사용자를 찾을 수 없습니다.");
		}
		Users users = _users.get();
		// 사용자 권한 객체
		List<GrantedAuthority> authorities = new ArrayList<>();
		if("amdin".equals(username)) {
			// 아이디가 admin 이면 admin권한
			authorities.add(new SimpleGrantedAuthority(UserRole.ADMIN.getValue()));
		}else {
			// 아니면 user권한
			authorities.add(new SimpleGrantedAuthority(UserRole.USER.getValue()));
		}
		
		// 아이디,비밀번호,권한 리턴
		return new User(users.getUserName(),users.getPassword(),authorities);
	}
	
}
