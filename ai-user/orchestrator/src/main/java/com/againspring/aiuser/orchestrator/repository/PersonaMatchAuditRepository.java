package com.againspring.aiuser.orchestrator.repository;

import com.againspring.aiuser.orchestrator.domain.PersonaMatchAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PersonaMatchAuditRepository extends JpaRepository<PersonaMatchAudit, Long> {

    List<PersonaMatchAudit> findByCorrelationIdOrderByIdAsc(String correlationId);
}
