package com.ex.final22c.repository.mypage;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ex.final22c.data.user.UserAddress;

public interface UserAddressRepository extends JpaRepository<UserAddress, Long> {

    // 리스트: 기본(Y) 먼저, 최신순
    List<UserAddress> findByUserNoOrderByIsDefaultDescAddressNoDesc(Long userNo);

    // 기본 해제
    @Modifying
    // N 일시 기본 주소를 해제, Y 일시 기본 주소를 설정
    @Query("update UserAddress ua set ua.isDefault = 'N' where ua.userNo = :userNo and ua.isDefault = 'Y'")
    int clearDefaultByUserNo(@Param("userNo") Long userNo);

    // 특정 주소를 기본으로
    @Modifying
    @Query("update UserAddress ua set ua.isDefault = 'Y' where ua.addressNo = :addressNo and ua.userNo = :userNo")
    int markDefault(@Param("userNo") Long userNo, @Param("addressNo") Long addressNo);
}