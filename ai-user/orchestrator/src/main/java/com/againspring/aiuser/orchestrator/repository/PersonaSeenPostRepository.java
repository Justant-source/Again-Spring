package com.againspring.aiuser.orchestrator.repository;

import com.againspring.aiuser.orchestrator.domain.PersonaSeenPost;
import com.againspring.aiuser.orchestrator.domain.PersonaSeenPostId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PersonaSeenPostRepository extends JpaRepository<PersonaSeenPost, PersonaSeenPostId> {
    boolean existsByPersonaIdAndPostId(String personaId, String postId);
}
