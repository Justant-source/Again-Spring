package com.againspring.repository.marketing;

import com.againspring.domain.marketing.SocialSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 소셜 플랫폼 세션 저장소
 */
@Repository
public interface SocialSessionRepository extends JpaRepository<SocialSession, Long> {

    /**
     * 플랫폼별 세션 조회
     */
    Optional<SocialSession> findByPlatform(String platform);
}
