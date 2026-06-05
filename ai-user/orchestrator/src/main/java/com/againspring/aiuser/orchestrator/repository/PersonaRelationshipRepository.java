package com.againspring.aiuser.orchestrator.repository;

import com.againspring.aiuser.orchestrator.domain.PersonaRelationship;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface PersonaRelationshipRepository extends JpaRepository<PersonaRelationship, Long> {
    List<PersonaRelationship> findByPersonaIdAndStatus(String personaId, String status);
    List<PersonaRelationship> findByOtherIdAndStatus(String otherId, String status);
    /** COUPLE 또는 MARRIAGE 관계 전체 조회 (PairedPostScheduler용) */
    List<PersonaRelationship> findByRelationTypeInAndStatus(List<String> relationTypes, String status);
}
