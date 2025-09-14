
package com.ex.final22c.repository.user;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.ex.final22c.data.user.UserPreference;
import com.ex.final22c.data.user.Users;

@Repository
public interface UserPreferenceRepository extends JpaRepository<UserPreference, Long> {

    Optional<UserPreference> findByUser_UserName(String userName);
    Optional<UserPreference> findByUser_UserNo(Long userNo);

    boolean existsByUser(Users user);

    // 둘 중 하나만 사용하세요 (파생 메서드 권장)
    void deleteByUser_UserName(String userName);
}
