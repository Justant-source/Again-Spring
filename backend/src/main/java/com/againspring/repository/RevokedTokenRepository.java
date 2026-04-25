package com.againspring.repository;

import com.againspring.domain.RevokedToken;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * 취소된 JWT 토큰 저장소
 */
@Repository
public interface RevokedTokenRepository extends JpaRepository<RevokedToken, Long> {

    /**
     * JTI로 취소된 토큰 존재 여부 확인
     */
    boolean existsByJti(String jti);

    /**
     * 만료된 토큰 삭제
     */
    @Modifying
    @Query("DELETE FROM RevokedToken r WHERE r.expiresAt < :now")
    void deleteExpired(@Param("now") Instant now);
}
