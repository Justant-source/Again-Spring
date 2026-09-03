package com.againspring.aiuser.orchestrator.seed;

import com.againspring.aiuser.orchestrator.client.BackendInternalClient;
import com.againspring.aiuser.orchestrator.config.OrchestratorProperties;
import com.againspring.aiuser.orchestrator.domain.Persona;
import com.againspring.aiuser.orchestrator.repository.PersonaRelationshipRepository;
import com.againspring.aiuser.orchestrator.repository.PersonaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AiUserSeedLoaderDeletedSkipTest {

    @Test
    void deletedSkippedMarksPersonaInactiveWithoutResurrecting() {
        PersonaRepository personaRepo = mock(PersonaRepository.class);
        PersonaRelationshipRepository relationshipRepo = mock(PersonaRelationshipRepository.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        OrchestratorProperties props = new OrchestratorProperties();
        PersonaFactory personaFactory = mock(PersonaFactory.class);
        BackendInternalClient internalClient = mock(BackendInternalClient.class);

        AiUserSeedLoader loader = new AiUserSeedLoader(
            personaRepo, relationshipRepo, jdbcTemplate, props, personaFactory, internalClient);

        Persona existing = Persona.builder().id("p1").active(true).build();
        when(personaRepo.findById("p1")).thenReturn(Optional.of(existing));

        loader.applyUpsertOutcome("p1", Optional.of("DELETED_SKIPPED"));

        assertFalse(existing.isActive());
        verify(personaRepo).save(existing);
    }

    @Test
    void createdStatusDoesNotTouchPersonaRepo() {
        PersonaRepository personaRepo = mock(PersonaRepository.class);
        PersonaRelationshipRepository relationshipRepo = mock(PersonaRelationshipRepository.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        OrchestratorProperties props = new OrchestratorProperties();
        PersonaFactory personaFactory = mock(PersonaFactory.class);
        BackendInternalClient internalClient = mock(BackendInternalClient.class);

        AiUserSeedLoader loader = new AiUserSeedLoader(
            personaRepo, relationshipRepo, jdbcTemplate, props, personaFactory, internalClient);

        loader.applyUpsertOutcome("p1", Optional.of("CREATED"));

        verify(personaRepo, never()).findById(any());
        verify(personaRepo, never()).save(any());
    }

    @Test
    void emptyResultDoesNotCrash() {
        PersonaRepository personaRepo = mock(PersonaRepository.class);
        PersonaRelationshipRepository relationshipRepo = mock(PersonaRelationshipRepository.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        OrchestratorProperties props = new OrchestratorProperties();
        PersonaFactory personaFactory = mock(PersonaFactory.class);
        BackendInternalClient internalClient = mock(BackendInternalClient.class);

        AiUserSeedLoader loader = new AiUserSeedLoader(
            personaRepo, relationshipRepo, jdbcTemplate, props, personaFactory, internalClient);

        assertDoesNotThrow(() -> loader.applyUpsertOutcome("p1", Optional.empty()));
        verify(personaRepo, never()).findById(any());
    }
}
