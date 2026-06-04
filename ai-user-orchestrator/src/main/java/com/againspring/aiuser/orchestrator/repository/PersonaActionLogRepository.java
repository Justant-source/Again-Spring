package com.againspring.aiuser.orchestrator.repository;

import com.againspring.aiuser.orchestrator.domain.PersonaActionLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface PersonaActionLogRepository extends JpaRepository<PersonaActionLog, Long> {
    // Most recent action for a persona (for cooldown calculation)
    Optional<PersonaActionLog> findTopByPersonaIdOrderByCreatedAtDesc(String personaId);

    // Count actions today by type
    @Query("SELECT COUNT(a) FROM PersonaActionLog a WHERE a.actionType = :type AND a.createdAt >= :since")
    long countByActionTypeAndCreatedAtAfter(@Param("type") String type, @Param("since") Instant since);
}
