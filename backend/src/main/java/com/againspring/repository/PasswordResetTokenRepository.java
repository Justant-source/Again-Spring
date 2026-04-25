package com.againspring.repository;

import com.againspring.domain.PasswordResetToken;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * 비밀번호 재설정 토큰 저장소
 */
@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    /**
     * 토큰으로 아직 사용하지 않은 유효한 토큰 조회
     */
    Optional<PasswordResetToken> findByTokenAndUsedFalseAndExpiresAtAfter(String token, Instant now);

    /**
     * 이메일과 사용하지 않은 상태로 가장 최신의 토큰 조회
     */
    Optional<PasswordResetToken> findTopByEmailAndUsedFalseOrderByCreatedAtDesc(String email);
}
