package com.againspring.aiuser.orchestrator.repository;

import com.againspring.aiuser.orchestrator.domain.Persona;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface PersonaRepository extends JpaRepository<Persona, String> {
    List<Persona> findByActiveTrue();
}
