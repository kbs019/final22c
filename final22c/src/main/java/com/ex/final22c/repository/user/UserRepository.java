package com.ex.final22c.repository.user;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ex.final22c.data.user.Users;

public interface UserRepository extends JpaRepository<Users, Long> {

    Optional<Users> findByUserName(String userName);

    Optional<Users> findByEmail(String email);

    Optional<Users> findByPhone(String phone);
}


