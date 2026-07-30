package com.againspring.aiuser.orchestrator.repository;

import com.againspring.aiuser.orchestrator.domain.AiThreadPlan;
import com.againspring.aiuser.orchestrator.domain.enums.ThreadPlanStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface AiThreadPlanRepository extends JpaRepository<AiThreadPlan, String> {
    Optional<AiThreadPlan> findByPostIdAndPostRevision(String postId, int postRevision);
    Optional<AiThreadPlan> findTopByPostIdOrderByPostRevisionDesc(String postId);

    List<AiThreadPlan> findByStatusInAndAbsoluteExpiresAtBefore(Collection<ThreadPlanStatus> statuses, Instant now);

    List<AiThreadPlan> findByStatusIn(Collection<ThreadPlanStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from AiThreadPlan p where p.status = :status order by p.createdAt asc")
    List<AiThreadPlan> lockByStatus(@Param("status") ThreadPlanStatus status, Pageable pageable);

    List<AiThreadPlan> findByPostIdAndPostRevisionLessThanAndStatusIn(String postId, int postRevision,
                                                                        Collection<ThreadPlanStatus> statuses);

    @Modifying
    @Query("update AiThreadPlan p set p.status = :cancelled where p.postId = :postId and p.postRevision < :revision " +
           "and p.status in :activeStatuses")
    int cancelOlderActivePlans(@Param("postId") String postId,
                               @Param("revision") int revision,
                               @Param("cancelled") ThreadPlanStatus cancelled,
                               @Param("activeStatuses") Collection<ThreadPlanStatus> activeStatuses);
}
