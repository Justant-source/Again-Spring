package com.againspring.aiuser.orchestrator.repository;

import com.againspring.aiuser.orchestrator.domain.Persona;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface PersonaRepository extends JpaRepository<Persona, String> {
    List<Persona> findByActiveTrue();

    /** WP1 계약 2 — 정렬된 활성 페르소나 id 목록(PersonaQuotaPlanner 입력). */
    @org.springframework.data.jpa.repository.Query("SELECT p.id FROM Persona p WHERE p.active = true ORDER BY p.id ASC")
    List<String> findActiveIdsOrderById();
}
