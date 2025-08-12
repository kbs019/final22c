package com.ex.final22c.service.user;


import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;


import com.ex.final22c.data.user.Users;
import com.ex.final22c.form.UsersForm;
import com.ex.final22c.repository.user.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UsersService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    	
    	// 회원가입
        public Users create(UsersForm usersForm) {
            Users user = Users.builder()
                .userName(usersForm.getUserName())
                .password(passwordEncoder.encode(usersForm.getPassword1()))
                .email(usersForm.getEmail())
                .name(usersForm.getName())
                .birth(usersForm.getBirth())
                .telecom(usersForm.getTelecom())
                .phone(usersForm.getPhone())
                .gender(usersForm.getGender())
                .loginType("local")
                .build();
            this.userRepository.save(user);
            return user;
        }
        
}