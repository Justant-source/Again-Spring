package com.againspring.repository.marketing;

import com.againspring.domain.marketing.SocialCredential;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 소셜 플랫폼 자격증 저장소
 */
@Repository
public interface SocialCredentialRepository extends JpaRepository<SocialCredential, Long> {

    /**
     * 플랫폼별 자격증 조회
     */
    Optional<SocialCredential> findByPlatform(String platform);
}
