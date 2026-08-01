package com.againspring.aiuser.orchestrator.repository;

import com.againspring.aiuser.orchestrator.domain.PersonaFactAssertion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PersonaFactAssertionRepository extends JpaRepository<PersonaFactAssertion, Long> {

    List<PersonaFactAssertion> findByPersonaId(String personaId);

    Optional<PersonaFactAssertion> findByPersonaIdAndFactKey(String personaId, String factKey);
}
