package com.ex.final22c.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ex.final22c.data.UserAddress;

public interface UserAddressRepository extends JpaRepository<UserAddress, Long> {
    List<UserAddress> findByUserNo(Long userNo);
}