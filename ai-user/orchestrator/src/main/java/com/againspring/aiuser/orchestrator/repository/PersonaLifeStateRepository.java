package com.againspring.aiuser.orchestrator.repository;

import com.againspring.aiuser.orchestrator.domain.PersonaLifeState;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PersonaLifeStateRepository extends JpaRepository<PersonaLifeState, String> {
}
