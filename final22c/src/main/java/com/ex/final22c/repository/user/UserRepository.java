package com.ex.final22c.repository.user;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


import com.ex.final22c.data.user.Users;
@Repository
public interface UserRepository extends JpaRepository<Users, Long> {

    Optional<Users> findByUserName(String userName);

    Optional<Users> findByEmail(String email);

    Optional<Users> findByPhone(String phone);
    
    // 유저목록 (페이징,검색)
    Page<Users> findAll(Specification<Users> spec,Pageable pageable);
}


