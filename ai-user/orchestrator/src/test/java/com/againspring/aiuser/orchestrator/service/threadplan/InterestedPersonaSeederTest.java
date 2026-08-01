package com.againspring.aiuser.orchestrator.service.threadplan;

import com.againspring.aiuser.orchestrator.domain.AiPostInterestedPersona;
import com.againspring.aiuser.orchestrator.repository.AiPostInterestedPersonaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InterestedPersonaSeederTest {

    @Mock private AiPostInterestedPersonaRepository repository;
    private InterestedPersonaSeeder seeder;

    @BeforeEach
    void setUp() {
        seeder = new InterestedPersonaSeeder(repository);
    }

    @Test
    void seedFromPlanCastInsertsUniquePersonas() {
        when(repository.existsByPostIdAndPersonaId("post-1", "p1")).thenReturn(false);
        when(repository.existsByPostIdAndPersonaId("post-1", "p2")).thenReturn(true);
        when(repository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        seeder.seedFromPlanCast("post-1", List.of("p1", "p2", "p1", " "));

        ArgumentCaptor<AiPostInterestedPersona> captor = ArgumentCaptor.forClass(AiPostInterestedPersona.class);
        verify(repository, times(1)).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getPostId()).isEqualTo("post-1");
        assertThat(captor.getValue().getPersonaId()).isEqualTo("p1");
        assertThat(captor.getValue().getSource()).isEqualTo(AiPostInterestedPersona.SOURCE_PLAN_CAST);
    }

    @Test
    void seedFromPlanCastNoopsOnBlankPost() {
        seeder.seedFromPlanCast(" ", List.of("p1"));
        verifyNoInteractions(repository);
    }
}
