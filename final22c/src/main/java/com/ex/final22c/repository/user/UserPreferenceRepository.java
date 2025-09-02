
package com.ex.final22c.repository.user;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.ex.final22c.data.user.UserPreference;
import com.ex.final22c.data.user.Users;

@Repository
public interface UserPreferenceRepository extends JpaRepository<UserPreference, Long> {
    
    /**
     * 사용자명으로 사용자 선호도 조회
     */
    Optional<UserPreference> findByUser_UserName(String userName);
    
    /**
     * 사용자 번호로 사용자 선호도 조회
     */
    Optional<UserPreference> findByUser_UserNo(Long userNo);
    
    /**
     * 사용자명으로 사용자 선호도 삭제
     */
    void deleteByUser_UserName(String userName);
    boolean existsByUser(Users user);
}