package com.ex.final22c.repository.mypage;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ex.final22c.data.user.UserAddress;

public interface UserAddressRepository extends JpaRepository<UserAddress, Long> {

    /* 목록: 기본(Y) 먼저, 최신순 */
    @Query("""
              select ua
                from UserAddress ua
               where ua.user.userNo = :userNo
               order by ua.isDefault desc, ua.addressNo desc
            """)
    List<UserAddress> findSortedByUserNo(@Param("userNo") Long userNo);

    /* 사용자 주소 전체 */
    List<UserAddress> findByUser_UserNo(Long userNo);

    /* 기본 해제 */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
              update UserAddress ua
                 set ua.isDefault = 'N'
               where ua.user.userNo = :userNo
                 and ua.isDefault = 'Y'
            """)
    int clearDefaultByUserNo(@Param("userNo") Long userNo);

    /* 특정 주소를 기본으로 */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
              update UserAddress ua
                 set ua.isDefault = 'Y'
               where ua.user.userNo = :userNo
                 and ua.addressNo = :addressNo
            """)
    int markDefault(@Param("userNo") Long userNo, @Param("addressNo") Long addressNo);

    /* 기본 주소 조회 (없을 수 있으므로 Optional) */
    @Query("""
              select ua
                from UserAddress ua
               where ua.user.userNo = :userNo
                 and ua.isDefault = 'Y'
            """)
    Optional<UserAddress> findDefaultByUserNo(@Param("userNo") Long userNo);

    Optional<UserAddress> findByUser_UserNoAndAddressNo(Long userNo, Long addressNo);

    void deleteByUser_UserNoAndAddressNo(Long userNo, Long addressNo);

    long countByUser_UserNo(Long userNo);

    /* 새 기본 지정 후 다른 모든 기본 해제 (방어적) */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
              update UserAddress a
                 set a.isDefault = 'N'
               where a.user.userNo = :userNo
                 and a.addressNo <> :addressNo
            """)
    int unsetDefaultExcept(@Param("userNo") Long userNo, @Param("addressNo") Long addressNo);
}
