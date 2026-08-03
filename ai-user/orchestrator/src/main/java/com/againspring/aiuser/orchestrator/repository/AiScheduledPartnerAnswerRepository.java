package com.againspring.aiuser.orchestrator.repository;

import com.againspring.aiuser.orchestrator.domain.AiScheduledPartnerAnswer;
import com.againspring.aiuser.orchestrator.domain.enums.ScheduledPartnerAnswerStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface AiScheduledPartnerAnswerRepository extends JpaRepository<AiScheduledPartnerAnswer, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from AiScheduledPartnerAnswer s where s.status = :status and s.scheduledPartnerAt <= :now " +
           "order by s.scheduledPartnerAt asc")
    List<AiScheduledPartnerAnswer> lockDueItems(@Param("status") ScheduledPartnerAnswerStatus status,
                                                @Param("now") Instant now, Pageable pageable);

    Optional<AiScheduledPartnerAnswer> findByPostId(String postId);

    long countByStatus(ScheduledPartnerAnswerStatus status);
}
