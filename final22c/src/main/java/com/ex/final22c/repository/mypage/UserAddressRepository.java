package com.ex.final22c.repository.mypage;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ex.final22c.data.user.UserAddress;

public interface UserAddressRepository extends JpaRepository<UserAddress, Long> {

    // // 리스트: 기본(Y) 먼저, 최신순
    // List<UserAddress> findByUserNoOrderByIsDefaultDescAddressNoDesc(Long userNo);

    // 유저 번호로 주소 목록 조회
    List<UserAddress> findByUser_UserNo(Long userNo);

    // 기본 주소 설정
    @Modifying
    @Query("update UserAddress ua set ua.isDefault = 'N' where ua.user.userNo = :userNo and ua.isDefault = 'Y'")
    int clearDefaultByUserNo(@Param("userNo") Long userNo);

    // 특정 주소를 기본으로
    @Modifying
    @Query("update UserAddress ua set ua.isDefault = 'Y' where ua.addressNo = :addressNo and ua.user.userNo = :userNo")
    int markDefault(@Param("userNo") Long userNo, @Param("addressNo") Long addressNo);
    
    // 기본 주소지 조회
    @Query("select ua from UserAddress ua where ua.user.userNo = :userNo and ua.isDefault = 'Y'")
    UserAddress findDefaultByUserNo(@Param("userNo") Long userNo);
}