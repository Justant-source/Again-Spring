package com.againspring.api.internal;

import com.againspring.domain.ai.SystemSetting;
import com.againspring.domain.marketing.XPersonaExample;
import com.againspring.marketing.AsmProperties;
import com.againspring.marketing.XPersonaShadowEval;
import com.againspring.repository.ai.SystemSettingRepository;
import com.againspring.repository.marketing.XPersonaEvalRepository;
import com.againspring.repository.marketing.XPersonaExampleRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PersonaExportControllerTest {

    @Mock
    AsmProperties asmProperties;

    @Mock
    SystemSettingRepository systemSettingRepository;

    @Mock
    XPersonaExampleRepository exampleRepository;

    @Mock
    XPersonaEvalRepository evalRepository;

    @Mock
    XPersonaShadowEval shadowEval;

    PersonaExportController controller;

    private static final String VALID_TOKEN = "test-callback-token";

    @BeforeEach
    void setUp() {
        when(asmProperties.getCallbackToken()).thenReturn(VALID_TOKEN);
        controller = new PersonaExportController(
                new InternalTokenGuard(asmProperties),
                systemSettingRepository,
                exampleRepository,
                evalRepository,
                shadowEval,
                new ObjectMapper());
    }

    @Test
    void export_missingToken_returns401() {
        ResponseEntity<PersonaExportController.PersonaExportResponse> response =
                controller.export(null, 0L, 0L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(exampleRepository, never()).findTop500ByIdGreaterThanOrderByIdAsc(anyLong());
    }

    @Test
    void export_mismatchToken_returns401() {
        ResponseEntity<PersonaExportController.PersonaExportResponse> response =
                controller.export("Bearer wrong-token", 0L, 0L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(exampleRepository, never()).findTop500ByIdGreaterThanOrderByIdAsc(anyLong());
    }

    @Test
    void export_validToken_returns200WithSettingsAndEmptyEvals() {
        when(systemSettingRepository.findById(PersonaExportController.KEY_PROFILE))
                .thenReturn(Optional.of(SystemSetting.builder()
                        .settingKey(PersonaExportController.KEY_PROFILE)
                        .settingValue("{\"summary\":\"한 줄\"}")
                        .build()));
        when(systemSettingRepository.findById(PersonaExportController.KEY_PROFILE_PREV))
                .thenReturn(Optional.empty());
        when(systemSettingRepository.findById(PersonaExportController.KEY_LAST_STATUS))
                .thenReturn(Optional.of(SystemSetting.builder()
                        .settingKey(PersonaExportController.KEY_LAST_STATUS)
                        .settingValue("INGESTED")
                        .build()));
        when(systemSettingRepository.findById(PersonaExportController.KEY_LAST_LEARNED))
                .thenReturn(Optional.of(SystemSetting.builder()
                        .settingKey(PersonaExportController.KEY_LAST_LEARNED)
                        .settingValue("2026-09-01T04:30:00Z")
                        .build()));
        when(systemSettingRepository.findById(PersonaExportController.KEY_LAST_NEW))
                .thenReturn(Optional.of(SystemSetting.builder()
                        .settingKey(PersonaExportController.KEY_LAST_NEW)
                        .settingValue("3")
                        .build()));
        when(evalRepository.findTop500ByIdGreaterThanOrderByIdAsc(0L)).thenReturn(List.of());
        when(shadowEval.metrics()).thenReturn(
                new XPersonaShadowEval.MimicryMetrics(0, 0, null, false, true));
        Instant created = Instant.parse("2026-08-31T12:00:00Z");
        when(exampleRepository.findTop500ByIdGreaterThanOrderByIdAsc(0L)).thenReturn(List.of(
                XPersonaExample.builder()
                        .id(11L)
                        .source(XPersonaExample.Source.TIMELINE)
                        .tweetId("tw-1")
                        .postText("상황")
                        .hasPhoto(false)
                        .operatorBody("한 줄 ㅋㅋ")
                        .createdAt(created)
                        .build()));

        ResponseEntity<PersonaExportController.PersonaExportResponse> response =
                controller.export("Bearer " + VALID_TOKEN, 0L, 0L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        PersonaExportController.PersonaExportResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.generatedAt()).isNotNull();
        assertThat(body.profile()).isNotNull();
        assertThat(body.profilePrev()).isNull();
        assertThat(body.lastStatus()).isEqualTo("INGESTED");
        assertThat(body.lastLearnedAt()).isEqualTo("2026-09-01T04:30:00Z");
        assertThat(body.lastNewCount()).isEqualTo(3);
        assertThat(body.metrics()).containsEntry("sampleInsufficient", true);
        assertThat(body.evals()).isEmpty();
        assertThat(body.examples()).hasSize(1);
        assertThat(body.examples().get(0).id()).isEqualTo(11L);
        assertThat(body.examples().get(0).source()).isEqualTo("TIMELINE");
        assertThat(body.examples().get(0).tweetId()).isEqualTo("tw-1");
    }

    @Test
    void export_sinceExampleId_filtersRepository() {
        when(evalRepository.findTop500ByIdGreaterThanOrderByIdAsc(7L)).thenReturn(List.of());
        when(shadowEval.metrics()).thenReturn(
                new XPersonaShadowEval.MimicryMetrics(0, 0, null, false, true));
        when(exampleRepository.findTop500ByIdGreaterThanOrderByIdAsc(42L)).thenReturn(List.of(
                XPersonaExample.builder()
                        .id(43L)
                        .source(XPersonaExample.Source.DELETED_AUTO)
                        .tweetId("tw-43")
                        .operatorBody("지운 자동")
                        .createdAt(Instant.parse("2026-09-01T00:00:00Z"))
                        .build()));

        ResponseEntity<PersonaExportController.PersonaExportResponse> response =
                controller.export("Bearer " + VALID_TOKEN, 42L, 7L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().examples()).extracting(PersonaExportController.ExampleDto::id)
                .containsExactly(43L);
        verify(exampleRepository).findTop500ByIdGreaterThanOrderByIdAsc(42L);
        verify(evalRepository).findTop500ByIdGreaterThanOrderByIdAsc(7L);
    }
}
