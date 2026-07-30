package com.againspring.aiuser.orchestrator.repository;

import com.againspring.aiuser.orchestrator.domain.AiHumanInteractionInbox;
import com.againspring.aiuser.orchestrator.domain.enums.HumanInteractionStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface AiHumanInteractionInboxRepository extends JpaRepository<AiHumanInteractionInbox, String> {
    Optional<AiHumanInteractionInbox> findBySourceCommentId(String sourceCommentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from AiHumanInteractionInbox i where i.status = :pending and i.expiresAt > :now order by i.observedAt asc")
    List<AiHumanInteractionInbox> lockPendingForBatch(@Param("pending") HumanInteractionStatus pending,
                                                      @Param("now") Instant now, Pageable pageable);

    @Modifying
    @Query("update AiHumanInteractionInbox i set i.status = :pending, i.leaseOwner = null, i.leaseUntil = null " +
           "where i.status = :processing and i.leaseUntil < :now")
    int recoverExpiredLeases(@Param("processing") HumanInteractionStatus processing,
                             @Param("pending") HumanInteractionStatus pending,
                             @Param("now") Instant now);

    @Modifying
    @Query("update AiHumanInteractionInbox i set i.status = :expired where i.status in (:pending, :processing) and i.expiresAt <= :now")
    int expirePastDue(@Param("pending") HumanInteractionStatus pending,
                      @Param("processing") HumanInteractionStatus processing,
                      @Param("expired") HumanInteractionStatus expired,
                      @Param("now") Instant now);
}
