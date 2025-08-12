package com.ex.final22c.repository.mypage;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ex.final22c.data.user.UserAddress;

public interface UserAddressRepository extends JpaRepository<UserAddress, Long> {
    // [동작] + By + [조건필드] + [연산/연결] + [정렬조건]
    // 내 배송지 목록 조회
    List<UserAddress> findByUserNo(Long userNo);
    
}