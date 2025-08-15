package com.ex.final22c.repository.user;

import java.util.Optional;


import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.ex.final22c.data.user.Users;
@Repository
public interface UserRepository extends JpaRepository<Users, Long> {

    Optional<Users> findByUserName(String userName);

    Optional<Users> findByEmail(String email);

    Optional<Users> findByPhone(String phone);

    Optional<Users> findById(Long userNo);    
    
    // 유저목록 (페이징,검색)
    Page<Users> findAll(Specification<Users> spec, Pageable pageable);


    // 결제 승인 시 사용 마일리지 차감
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        UPDATE Users u
           SET u.mileage = u.mileage - :used
         WHERE u.userNo = :userNo
           AND u.mileage >= :used
    """)
    int deductMileage(@Param("userNo") Long userNo, @Param("used") int used);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        UPDATE Users u
           SET u.mileage = u.mileage + :amount
         WHERE u.userNo = :userNo
    """)
    int addMileage(@Param("userNo") Long userNo, @Param("amount") int amount);

}


