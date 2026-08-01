package com.againspring.aiuser.orchestrator.repository;

import com.againspring.aiuser.orchestrator.domain.PersonaSemanticCapsule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface PersonaSemanticCapsuleRepository extends JpaRepository<PersonaSemanticCapsule, Long> {

    List<PersonaSemanticCapsule> findByPersonaIdAndActiveTrue(String personaId);

    Optional<PersonaSemanticCapsule> findByPersonaIdAndCapsuleTypeAndTopicKey(
            String personaId, String capsuleType, String topicKey);

    /** @Modifying requires an active transaction (caller may not be @Transactional via self-invoke). */
    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("update PersonaSemanticCapsule c set c.active = false, c.updatedAt = CURRENT_TIMESTAMP " +
           "where c.personaId = :personaId and c.active = true and c.id not in :keepIds")
    int deactivateExcept(@Param("personaId") String personaId,
                         @Param("keepIds") Collection<Long> keepIds);
}
