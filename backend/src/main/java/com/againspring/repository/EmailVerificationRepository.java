package com.againspring.repository;

import com.againspring.domain.EmailVerification;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import java.util.Optional;

public interface EmailVerificationRepository extends JpaRepository<EmailVerification, Long> {

    Optional<EmailVerification> findTopByEmailAndUsedFalseAndExpiresAtAfterOrderByCreatedAtDesc(
            String email, Instant now);

    void deleteByEmail(String email);

    /** Admin 시스템 헬스 — 가장 최근 인증코드 발송 시각 (SMTP 살아있는지 신호) */
    Optional<EmailVerification> findTopByOrderByCreatedAtDesc();
}
