package com.ex.final22c.repository.user;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ex.final22c.data.user.UserAddress;

public interface UserAddressRepository extends JpaRepository<UserAddress, Long> {
    List<UserAddress> findByUserNo(Long userNo);
}