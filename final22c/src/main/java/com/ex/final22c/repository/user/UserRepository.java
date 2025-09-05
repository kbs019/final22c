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
import org.springframework.transaction.annotation.Transactional;

import com.ex.final22c.data.product.Product;
import com.ex.final22c.data.user.Users;

@Repository
public interface UserRepository extends JpaRepository<Users, Long> {

  Optional<Users> findByUserName(String userName);

  Optional<Users> findByEmail(String email);

  Optional<Users> findByPhone(String phone);

  Optional<Users> findById(Long userNo);

  boolean existsByUserName(String userName);

  boolean existsByEmail(String email);

  boolean existsByPhone(String phone); // DB엔 숫자만 11자리

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

  // 결제 취소시 마일리지 복구
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("""
          UPDATE Users u
             SET u.mileage = u.mileage + :amount
           WHERE u.userNo = :userNo
      """)
  int addMileage(@Param("userNo") Long userNo, @Param("amount") int amount);

  // 주문 확정시 마일리지 적립
  @Modifying
  @Query("""
          UPDATE Users u
             SET u.mileage = u.mileage + :mileage
           WHERE u.userNo = :userNo
      """)
  int addPoint(@Param("userNo") Long userNo, @Param("mileage") int mileage);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Transactional
  @Query(value = """
          UPDATE users
             SET age = EXTRACT(YEAR FROM SYSDATE) - EXTRACT(YEAR FROM birth) + 1
           WHERE birth IS NOT NULL
      """, nativeQuery = true)
  int refreshAgesForNewYear();

  // 내 관심목록
  // userName으로 PK만 뽑아오기(경량)
  @Query("select u.userNo from Users u where u.userName = :userName")
  Optional<Long> findUserNoByUserName(@Param("userName") String userName);
}