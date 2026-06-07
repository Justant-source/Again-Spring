package com.againspring.aiuser.orchestrator.repository;

import com.againspring.aiuser.orchestrator.domain.PersonaDailyQuota;
import com.againspring.aiuser.orchestrator.domain.PersonaDailyQuotaId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface PersonaDailyQuotaRepository extends JpaRepository<PersonaDailyQuota, PersonaDailyQuotaId> {
    Optional<PersonaDailyQuota> findByPersonaIdAndDayBucket(String personaId, LocalDate dayBucket);

    List<PersonaDailyQuota> findByDayBucket(LocalDate dayBucket);

    @Query("SELECT pdq FROM PersonaDailyQuota pdq WHERE pdq.dayBucket = :dayBucket")
    List<PersonaDailyQuota> findAllForDate(@Param("dayBucket") LocalDate dayBucket);
}
