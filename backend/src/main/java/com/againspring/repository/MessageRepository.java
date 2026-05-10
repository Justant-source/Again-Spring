package com.againspring.repository;

import com.againspring.domain.Message;
import com.againspring.domain.enums.MessageSender;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;

/**
 * MessageRepository (V1.5 카톡식 채팅)
 */
public interface MessageRepository extends JpaRepository<Message, Long> {

    List<Message> findBySessionIdOrderByCreatedAtAsc(String sessionId);

    @Query("SELECT m FROM Message m WHERE m.sessionId = :sessionId " +
           "AND m.createdAt > :since ORDER BY m.createdAt ASC")
    List<Message> findNewMessagesSince(@Param("sessionId") String sessionId,
                                        @Param("since") Instant since);

    @Query("SELECT m FROM Message m WHERE m.sessionId = :sessionId " +
           "AND m.sender IN :senders ORDER BY m.createdAt ASC")
    List<Message> findBySessionIdAndSenderIn(@Param("sessionId") String sessionId,
                                              @Param("senders") List<MessageSender> senders);

    long countBySessionIdAndSender(String sessionId, MessageSender sender);

    boolean existsBySessionIdAndSenderAndIsFinalizeSuggestionTrue(String sessionId, MessageSender sender);

    List<Message> findBySessionIdAndSenderAndIsFinalizeSuggestionTrueAndDismissedAtIsNull(
            String sessionId, MessageSender sender);

    @Modifying
    @Query("UPDATE Message m SET m.content = NULL WHERE m.sessionId IN :sessionIds")
    int nullifyContentBySessionIds(@Param("sessionIds") List<String> sessionIds);

    /** Admin 위기 모니터링용 — 메타데이터만 조회, content 노출 금지 */
    @Query("SELECT m FROM Message m WHERE m.crisisLevel IS NOT NULL AND m.crisisLevel >= 1 " +
           "ORDER BY m.createdAt DESC")
    List<Message> findRecentCrisisMessages(org.springframework.data.domain.Pageable pageable);

    /** V11 시스템 헬스 — 최근 N시간 내 mediator 메시지 총 수 */
    @Query("SELECT COUNT(m) FROM Message m WHERE m.sender IN " +
           "(com.againspring.domain.enums.MessageSender.MEDIATOR_TO_A, com.againspring.domain.enums.MessageSender.MEDIATOR_TO_B) " +
           "AND m.createdAt >= :since")
    long countMediatorMessagesSince(@Param("since") Instant since);

    /** V11 시스템 헬스 — 최근 N시간 내 fallback mediator 메시지 수 */
    @Query("SELECT COUNT(m) FROM Message m WHERE m.sender IN " +
           "(com.againspring.domain.enums.MessageSender.MEDIATOR_TO_A, com.againspring.domain.enums.MessageSender.MEDIATOR_TO_B) " +
           "AND m.createdAt >= :since AND m.llmModel LIKE '%-fallback'")
    long countMediatorFallbacksSince(@Param("since") Instant since);

    /** V11 LLM 실패율 차트 — 일별 mediator 메시지 + fallback 카운트 (native, KST 기준) */
    @Query(value = "SELECT DATE(CONVERT_TZ(m.created_at, '+00:00', '+09:00')) AS d, " +
                   "       COUNT(*) AS total, " +
                   "       SUM(CASE WHEN m.llm_model LIKE '%-fallback' THEN 1 ELSE 0 END) AS fb " +
                   "FROM messages m " +
                   "WHERE m.sender IN ('MEDIATOR_TO_A','MEDIATOR_TO_B') " +
                   "  AND m.created_at >= :since " +
                   "GROUP BY DATE(CONVERT_TZ(m.created_at, '+00:00', '+09:00')) " +
                   "ORDER BY d ASC", nativeQuery = true)
    List<Object[]> aggregateMediatorByDay(@Param("since") Instant since);

    /** V11 시스템 헬스 — 최근 mediator 호출 시각 (Anthropic 살아있는지 신호) */
    @Query("SELECT MAX(m.createdAt) FROM Message m WHERE m.sender IN " +
           "(com.againspring.domain.enums.MessageSender.MEDIATOR_TO_A, com.againspring.domain.enums.MessageSender.MEDIATOR_TO_B)")
    Instant findLastMediatorCallAt();
}
