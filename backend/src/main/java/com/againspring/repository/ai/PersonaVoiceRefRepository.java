package com.againspring.repository.ai;

import com.againspring.domain.ai.PersonaVoiceRef;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PersonaVoiceRefRepository extends JpaRepository<PersonaVoiceRef, String> {
}
