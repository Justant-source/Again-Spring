package com.againspring.aiuser.orchestrator.repository;

import com.againspring.aiuser.orchestrator.domain.AiScheduledPost;
import com.againspring.aiuser.orchestrator.domain.enums.ScheduledPostStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;

@Repository
public interface AiScheduledPostRepository extends JpaRepository<AiScheduledPost, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from AiScheduledPost s where s.status = :status and s.scheduledPublishAt <= :now " +
           "order by s.scheduledPublishAt asc")
    List<AiScheduledPost> lockDueItems(@Param("status") ScheduledPostStatus status,
                                       @Param("now") Instant now, Pageable pageable);

    long countByStatus(ScheduledPostStatus status);

    List<AiScheduledPost> findByStatusOrderByScheduledPublishAtAsc(ScheduledPostStatus status);
}
