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

    /**
     * 게스트가 만든 세션 중 cutoff 이전 생성된 것 조회 (7일 만료 정리용)
     */
    @Query("SELECT s FROM Session s WHERE s.createdByUserId IN " +
           "(SELECT u.id FROM User u WHERE u.isGuest = true) AND s.createdAt < :cutoff")
    List<Session> findOldGuestSessions(@Param("cutoff") Instant cutoff);

    /**
     * 사용자가 특정 기간에 만든 세션 수 (일일 세션 한도용)
     */
    @Query("SELECT COUNT(s) FROM Session s WHERE s.createdByUserId = :userId " +
           "AND s.createdAt >= :from AND s.createdAt < :to")
    int countByCreatedByUserIdAndCreatedAtBetween(
            @Param("userId") String userId,
            @Param("from") Instant from,
            @Param("to") Instant to);

    @Query("SELECT COUNT(s) FROM Session s WHERE s.createdAt >= :from AND s.createdAt < :to")
    long countByCreatedAtBetween(@Param("from") Instant from, @Param("to") Instant to);

    @Query("SELECT COUNT(s) FROM Session s WHERE s.status = :status AND s.createdAt >= :from AND s.createdAt < :to")
    long countByStatusAndCreatedAtBetween(@Param("status") SessionStatus status,
            @Param("from") Instant from, @Param("to") Instant to);

    @Query("SELECT COUNT(s) FROM Session s WHERE s.createdByUserId IN " +
           "(SELECT u.id FROM User u WHERE u.isGuest = true) AND s.createdAt >= :from AND s.createdAt < :to")
    long countGuestSessionsBetween(@Param("from") Instant from, @Param("to") Instant to);

    @Query("SELECT COALESCE(AVG(s.userAMessageCount + COALESCE(s.userBMessageCount, 0)), 0) " +
           "FROM Session s WHERE s.createdAt >= :from AND s.createdAt < :to")
    Double avgTurnsBetween(@Param("from") Instant from, @Param("to") Instant to);

    /** Admin 사용자 상세용 — 사용자가 관여한(생성/초대받은) 모든 세션 카운트 */
    @Query("SELECT COUNT(s) FROM Session s WHERE s.createdByUserId = :userId OR s.inviteeUserId = :userId")
    long countByUserInvolvement(@Param("userId") String userId);

    /** Admin 사용자 상세용 — 사용자 관여 세션 중 완료된 것 카운트 */
    @Query("SELECT COUNT(s) FROM Session s WHERE (s.createdByUserId = :userId OR s.inviteeUserId = :userId) " +
           "AND s.status = com.againspring.domain.enums.SessionStatus.COMPLETED")
    long countCompletedByUserInvolvement(@Param("userId") String userId);

    /** Admin 사용자 상세용 — 사용자 관여 세션 중 가장 최근 생성 시각 */
    @Query("SELECT MAX(s.createdAt) FROM Session s WHERE s.createdByUserId = :userId OR s.inviteeUserId = :userId")
    Optional<Instant> findLastSessionCreatedAt(@Param("userId") String userId);
}
