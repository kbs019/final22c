package com.ex.final22c.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.ex.final22c.data.UserAddress;
import com.ex.final22c.repository.UserAddressRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserAddressService {
    private final UserAddressRepository repo;

    public List<UserAddress> getMyAddresses(Long userNo) {
        return repo.findByUserNo(userNo);
    }
}