package com.againspring.aiuser.orchestrator.service.threadplan;

import com.againspring.aiuser.orchestrator.config.OrchestratorProperties;
import com.againspring.aiuser.orchestrator.domain.AiScheduledPost;
import com.againspring.aiuser.orchestrator.domain.enums.ScheduledPostStatus;
import com.againspring.aiuser.orchestrator.repository.AiScheduledPostRepository;
import com.againspring.aiuser.orchestrator.repository.PersonaRepository;
import com.againspring.aiuser.orchestrator.safety.ContentSafetyGuard;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminScheduledPostServiceTest {

    @Mock private AiScheduledPostRepository repository;
    @Mock private PersonaRepository personaRepository;
    @Mock private ContentSafetyGuard safetyGuard;
    @Mock private OrchestratorProperties properties;
    @Mock private OrchestratorProperties.ThreadPlan threadPlanConfig;

    private CandidateScheduleSupport scheduleSupport;
    private AdminScheduledPostService service;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        scheduleSupport = new CandidateScheduleSupport(properties);
        service = new AdminScheduledPostService(
                repository, personaRepository, safetyGuard, scheduleSupport, objectMapper);
    }

    private Map<Integer, Double> flatWeights() {
        Map<Integer, Double> w = new HashMap<>();
        for (int h = 0; h < 24; h++) w.put(h, 1.0);
        return w;
    }

    private AiScheduledPost scheduledRow(Instant slot, String candidatesJson) {
        return AiScheduledPost.builder()
                .id("hold-1")
                .personaId("persona-a")
                .category("COUPLE")
                .title("제목")
                .body("본문입니다")
                .candidatesJson(candidatesJson)
                .scheduledPublishAt(slot)
                .status(ScheduledPostStatus.SCHEDULED)
                .origin("NIGHTLY_BATCH")
                .createdAt(Instant.parse("2026-08-01T03:05:00Z"))
                .updatedAt(Instant.parse("2026-08-01T03:05:00Z"))
                .build();
    }

    @Test
    void enrichMissingScheduledAtsFillsIsoTimes() {
        when(properties.getThreadPlan()).thenReturn(threadPlanConfig);
        when(threadPlanConfig.getKstHourlyHumanWeights()).thenReturn(flatWeights());

        Instant slot = Instant.parse("2026-08-01T11:00:00Z"); // 20:00 KST
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("items", List.of(Map.of(
                "ref", "c1", "personaId", "p1", "body", "댓글"
        )));

        scheduleSupport.enrichMissingScheduledAts(response, slot);

        @SuppressWarnings("unchecked")
        Map<String, Object> item = ((List<Map<String, Object>>) response.get("items")).get(0);
        Instant at = Instant.parse(String.valueOf(item.get("scheduledAt")));
        assertThat(at).isEqualTo(scheduleSupport.schedule(slot, 0, false));
    }

    @Test
    void patchShiftsItemTimesWhenSlotMovesWithoutItems() throws Exception {
        Instant oldSlot = Instant.parse("2026-08-01T11:00:00Z");
        Instant newSlot = Instant.parse("2026-08-01T12:00:00Z");
        Instant itemAt = Instant.parse("2026-08-01T11:03:00Z");

        Map<String, Object> candidates = new LinkedHashMap<>();
        candidates.put("post", Map.of("title", "제목", "body", "본문입니다"));
        candidates.put("items", List.of(new LinkedHashMap<>(Map.of(
                "ref", "c1", "personaId", "p1", "body", "댓글", "scheduledAt", itemAt.toString()
        ))));
        AiScheduledPost row = scheduledRow(oldSlot, objectMapper.writeValueAsString(candidates));

        when(repository.findById("hold-1")).thenReturn(Optional.of(row));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> result = service.patch("hold-1", Map.of("scheduledPublishAt", newSlot.toString()));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) result.get("items");
        assertThat(Instant.parse(String.valueOf(items.get(0).get("scheduledAt"))))
                .isEqualTo(itemAt.plusSeconds(3600));
        assertThat(result.get("scheduledPublishAt")).isEqualTo(newSlot.toString());
    }

    @Test
    void cancelSetsCancelled() {
        AiScheduledPost row = scheduledRow(Instant.parse("2026-08-01T11:00:00Z"), "{}");
        when(repository.findById("hold-1")).thenReturn(Optional.of(row));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> result = service.cancel("hold-1");

        assertThat(result.get("status")).isEqualTo("CANCELLED");
        ArgumentCaptor<AiScheduledPost> captor = ArgumentCaptor.forClass(AiScheduledPost.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(ScheduledPostStatus.CANCELLED);
    }

    @Test
    void patchRejectsPublishing() {
        AiScheduledPost row = scheduledRow(Instant.parse("2026-08-01T11:00:00Z"), "{}");
        row.setStatus(ScheduledPostStatus.PUBLISHING);
        when(repository.findById("hold-1")).thenReturn(Optional.of(row));

        assertThatThrownBy(() -> service.patch("hold-1", Map.of("title", "x")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409");
    }
}
