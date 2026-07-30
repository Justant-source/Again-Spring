package com.againspring.aiuser.orchestrator.repository;

import com.againspring.aiuser.orchestrator.domain.AiThreadPlanItem;
import com.againspring.aiuser.orchestrator.domain.enums.ThreadPlanItemStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

@Repository
public interface AiThreadPlanItemRepository extends JpaRepository<AiThreadPlanItem, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from AiThreadPlanItem i where i.status = :status and i.scheduledAt <= :now " +
           "and (i.notBefore is null or i.notBefore <= :now) order by i.scheduledAt asc, i.sequenceNo asc")
    List<AiThreadPlanItem> lockDueItems(@Param("status") ThreadPlanItemStatus status,
                                        @Param("now") Instant now, Pageable pageable);

    @Query("select i from AiThreadPlanItem i where i.planId = :planId and i.status in :statuses")
    List<AiThreadPlanItem> findUnfinishedByPlanId(@Param("planId") String planId,
                                                   @Param("statuses") Collection<ThreadPlanItemStatus> statuses);

    @Modifying
    @Query("update AiThreadPlanItem i set i.status = :cancelled, i.leaseOwner = null, i.leaseUntil = null " +
           "where i.planId = :planId and i.status in :unfinished")
    int cancelUnfinishedByPlanId(@Param("planId") String planId,
                                 @Param("cancelled") ThreadPlanItemStatus cancelled,
                                 @Param("unfinished") Collection<ThreadPlanItemStatus> unfinished);

    @Modifying
    @Query("update AiThreadPlanItem i set i.status = :scheduled, i.leaseOwner = null, i.leaseUntil = null " +
           "where i.status = :processing and i.leaseUntil < :now")
    int recoverExpiredLeases(@Param("processing") ThreadPlanItemStatus processing,
                             @Param("scheduled") ThreadPlanItemStatus scheduled,
                             @Param("now") Instant now);
}
