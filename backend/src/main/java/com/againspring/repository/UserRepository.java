package com.againspring.repository;

import com.againspring.domain.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * 사용자 저장소 (JPA/MariaDB)
 */
@Repository
public interface UserRepository extends JpaRepository<User, String> {

    /**
     * 이메일로 사용자 조회
     */
    Optional<User> findByEmail(String email);

    /**
     * ID로 사용자 조회 (소프트 삭제 제외)
     */
    Optional<User> findByIdAndDeletedAtIsNull(String id);

    /**
     * 이메일 존재 여부 확인
     */
    boolean existsByEmail(String email);

    /**
     * OAuth provider + providerId로 사용자 조회
     */
    Optional<User> findByProviderAndProviderId(String provider, String providerId);

    /**
     * 이메일 prefix로 사용자 목록 조회 (dev 테스트 데이터 정리용)
     */
    java.util.List<User> findByEmailStartingWith(String prefix);

    long countByIsGuestFalseAndCreatedAtBetween(java.time.Instant from, java.time.Instant to);

    java.util.List<User> findByNicknameContainingIgnoreCaseOrEmailContainingIgnoreCaseAndDeletedAtIsNull(
            String nickname, String email);
}
