package com.againspring.api.internal;

import com.againspring.domain.ai.SystemSetting;
import com.againspring.domain.marketing.XPersonaEval;
import com.againspring.domain.marketing.XPersonaExample;
import com.againspring.marketing.XPersonaShadowEval;
import com.againspring.repository.ai.SystemSettingRepository;
import com.againspring.repository.marketing.XPersonaEvalRepository;
import com.againspring.repository.marketing.XPersonaExampleRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Host-only persona vault mirror (Phase 5).
 * Doc-Sync: GET /api/internal/marketing/persona-export → docs/shared/50-api/rest-spec.md
 * Auth: Authorization Bearer ASM_CALLBACK_TOKEN via InternalTokenGuard (same as callback/redrive).
 */
@RestController
@RequestMapping("/api/internal/marketing")
@RequiredArgsConstructor
@Slf4j
public class PersonaExportController {

    static final String KEY_PROFILE = "marketing.x.persona_profile_json";
    static final String KEY_PROFILE_PREV = "marketing.x.persona_profile_prev_json";
    static final String KEY_LAST_STATUS = "marketing.x.persona_last_status";
    static final String KEY_LAST_LEARNED = "marketing.x.persona_last_learned_at";
    static final String KEY_LAST_NEW = "marketing.x.persona_last_new_count";
    static final int EXAMPLE_CAP = 500;

    private final InternalTokenGuard tokenGuard;
    private final SystemSettingRepository systemSettingRepository;
    private final XPersonaExampleRepository exampleRepository;
    private final XPersonaEvalRepository evalRepository;
    private final XPersonaShadowEval shadowEval;
    private final ObjectMapper objectMapper;

    @GetMapping("/persona-export")
    public ResponseEntity<PersonaExportResponse> export(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            @RequestParam(defaultValue = "0") long sinceExampleId,
            @RequestParam(defaultValue = "0") long sinceEvalId) {
        if (!tokenGuard.isAuthorized(authHeader)) {
            log.debug("Persona export rejected: invalid or missing token");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        long exampleSince = Math.max(0L, sinceExampleId);
        long evalSince = Math.max(0L, sinceEvalId);
        List<XPersonaExample> rows = exampleRepository.findTop500ByIdGreaterThanOrderByIdAsc(exampleSince);
        List<ExampleDto> examples = rows.stream().map(this::toDto).toList();
        List<EvalDto> evals = evalRepository.findTop500ByIdGreaterThanOrderByIdAsc(evalSince)
                .stream().map(this::toEvalDto).toList();
        return ResponseEntity.ok(new PersonaExportResponse(
                Instant.now(),
                parseJsonOrRaw(readSetting(KEY_PROFILE)),
                parseJsonOrRaw(readSetting(KEY_PROFILE_PREV)),
                readSetting(KEY_LAST_STATUS),
                readSetting(KEY_LAST_LEARNED),
                parseIntOrNull(readSetting(KEY_LAST_NEW)),
                metricsMap(),
                examples,
                evals));
    }

    private Map<String, Object> metricsMap() {
        try {
            XPersonaShadowEval.MimicryMetrics m = shadowEval.metrics();
            if (m == null) {
                return Map.of();
            }
            return Map.of(
                    "avg28d", m.avg28d(),
                    "sampleCount", m.sampleCount(),
                    "deleteRate28d", m.deleteRate28d() == null ? 0 : m.deleteRate28d(),
                    "gatePassed", m.gatePassed(),
                    "sampleInsufficient", m.sampleInsufficient());
        } catch (Exception e) {
            log.debug("Persona export metrics skipped: {}", e.getMessage());
            return Map.of();
        }
    }

    private EvalDto toEvalDto(XPersonaEval row) {
        return new EvalDto(
                row.getId(),
                row.getExampleId(),
                row.getTweetId(),
                row.getBotBody(),
                row.getScoreOverall(),
                row.getScoreTone(),
                row.getScoreLength(),
                row.getScoreTexture(),
                row.getScoreContent(),
                row.getJudgeNote(),
                row.getCreatedAt());
    }

    private ExampleDto toDto(XPersonaExample row) {
        return new ExampleDto(
                row.getId(),
                row.getSource() == null ? null : row.getSource().name(),
                row.getTweetId(),
                row.getPostText(),
                row.isHasPhoto(),
                row.getOperatorBody(),
                row.getCreatedAt());
    }

    private String readSetting(String key) {
        return systemSettingRepository.findById(key)
                .map(SystemSetting::getSettingValue)
                .orElse(null);
    }

    private Object parseJsonOrRaw(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(raw);
        } catch (Exception e) {
            return raw;
        }
    }

    private static Integer parseIntOrNull(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public record PersonaExportResponse(
            Instant generatedAt,
            Object profile,
            Object profilePrev,
            String lastStatus,
            String lastLearnedAt,
            Integer lastNewCount,
            Map<String, Object> metrics,
            List<ExampleDto> examples,
            List<EvalDto> evals
    ) {
    }

    public record EvalDto(
            Long id,
            Long exampleId,
            String tweetId,
            String botBody,
            Integer scoreOverall,
            Integer scoreTone,
            Integer scoreLength,
            Integer scoreTexture,
            Integer scoreContent,
            String judgeNote,
            Instant createdAt
    ) {
    }

    public record ExampleDto(
            Long id,
            String source,
            String tweetId,
            String postText,
            boolean hasPhoto,
            String operatorBody,
            Instant createdAt
    ) {
    }
}
