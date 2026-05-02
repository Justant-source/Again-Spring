package com.againspring.repository;

import com.againspring.domain.Session;
import com.againspring.domain.enums.SessionStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * 세션 저장소 (JPA/MariaDB)
 */
@Repository
public interface SessionRepository extends JpaRepository<Session, String> {

    /**
     * 초대 토큰으로 세션 조회
     */
    Optional<Session> findByInviteToken(String inviteToken);

    /**
     * 사용자가 생성했거나 초대받은 세션 조회 (생성순 역순)
     */
    List<Session> findByCreatedByUserIdOrInviteeUserIdOrderByCreatedAtDesc(
            String createdByUserId, String inviteeUserId);

    /**
     * 특정 상태의 만료된 초대 토큰 세션 조회 (타임아웃 정리용)
     */
    @Query("select s from Session s where s.status = :status and s.inviteExpiresAt < :now")
    List<Session> findByStatusAndInviteExpiresAtBefore(
            @Param("status") SessionStatus status, @Param("now") Instant now);

    /**
     * 콘텐츠 만료 세션 조회 (보관 기한 만료 정리용)
     */
    @Query("select s from Session s where s.status in :statuses and s.contentExpiresAt < :now")
    List<Session> findExpiredForRetention(
            @Param("statuses") Collection<SessionStatus> statuses, @Param("now") Instant now);

    /**
     * 사용자가 생성한 세션 조회 (상태 필터)
     */
    List<Session> findByCreatedByUserId(String createdByUserId);

    /**
     * 사용자가 생성했으며 특정 상태인 세션 조회
     */
    List<Session> findByCreatedByUserIdAndStatusIn(String createdByUserId, List<SessionStatus> statuses);

    /**
     * 사용자가 초대 수신자이며 특정 상태인 세션 조회
     */
    List<Session> findByInviteeUserIdAndStatusIn(String inviteeUserId, List<SessionStatus> statuses);
}
