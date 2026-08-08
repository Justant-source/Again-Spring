package com.againspring.aiuser.orchestrator.repository;

import com.againspring.aiuser.orchestrator.domain.DailyPlannerRetryLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface DailyPlannerRetryLogRepository extends JpaRepository<DailyPlannerRetryLog, Long> {
    Optional<DailyPlannerRetryLog> findByDayBucket(LocalDate dayBucket);

    List<DailyPlannerRetryLog> findByStatus(String status);

    List<DailyPlannerRetryLog> findByStatusAndDayBucketBefore(String status, LocalDate cutoff);
}
