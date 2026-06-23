package com.againspring.aiuser.orchestrator.repository;

import com.againspring.aiuser.orchestrator.domain.PersonaHistoryEntry;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PersonaHistoryEntryRepository extends JpaRepository<PersonaHistoryEntry, Long> {

    List<PersonaHistoryEntry> findByPersonaIdAndEntryTypeOrderByCreatedAtDescIdDesc(
        String personaId, String entryType, Pageable pageable
    );
}
