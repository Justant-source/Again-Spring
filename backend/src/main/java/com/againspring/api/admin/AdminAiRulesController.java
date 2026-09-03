package com.againspring.api.admin;

import com.againspring.annotation.Auditable;
import com.againspring.domain.ai.AiContentCorrection;
import com.againspring.domain.ai.AiGlobalRule;
import com.againspring.domain.ai.AiPromptTemplate;
import com.againspring.domain.ai.PersonaVoiceRef;
import com.againspring.domain.ai.SystemSetting;
import com.againspring.repository.ai.AiContentCorrectionRepository;
import com.againspring.repository.ai.AiGlobalRuleRepository;
import com.againspring.repository.ai.AiPromptTemplateRepository;
import com.againspring.repository.ai.PersonaVoiceRefRepository;
import com.againspring.repository.ai.SystemSettingRepository;
import com.againspring.service.ai.AiCorrectionService;
import com.againspring.service.ai.AiBatchLearningService;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 누적된 AI 전역 금지 규칙 · 페르소나 주의사항 관리 API (ADMIN 전용).
 * /admin/ai-rules 관리 화면의 백엔드.
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/ai-rules")
@RequiredArgsConstructor
@Tag(name = "Admin — AI Rules", description = "AI 전역 금지 규칙·페르소나 주의사항 관리 (ADMIN 전용)")
@SecurityRequirement(name = "bearer-jwt")
@PreAuthorize("hasRole('ADMIN')")
public class AdminAiRulesController {

    private final AiGlobalRuleRepository globalRuleRepository;
    private final AiContentCorrectionRepository correctionRepository;
    private final PersonaVoiceRefRepository personaVoiceRefRepository;
    private final AiCorrectionService aiCorrectionService;
    private final AiBatchLearningService aiBatchLearningService;
    private final AiPromptTemplateRepository promptTemplateRepository;
    private final ObjectMapper objectMapper;
    private final SystemSettingRepository systemSettingRepository;

    @Value("${llm.remote.base-url:http://againspring-llm:8090}")
    private String llmWorkerUrl;

    // =====================================================================
    // 전역 금지 규칙 관리
    // =====================================================================

    @GetMapping("/global")
    @Operation(summary = "전역 금지 규칙 목록", description = "누적된 전역 AI 금지 규칙을 조회한다.")
    public ResponseEntity<Page<AiGlobalRule>> listGlobalRules(
            @RequestParam(value = "active", required = false) Boolean active,
            Pageable pageable) {

        Page<AiGlobalRule> rules = (active != null)
                ? globalRuleRepository.findByActiveOrderByCreatedAtDesc(active, pageable)
                : globalRuleRepository.findAllByOrderByCreatedAtDesc(pageable);
        return ResponseEntity.ok(rules);
    }

    @PostMapping("/global")
    @Operation(summary = "전역 금지 규칙 수동 추가", description = "첨삭 없이 관리자가 직접 전역 규칙을 추가한다.")
    @Auditable(action = "AI_GLOBAL_RULE_CREATE", targetType = "AI_GLOBAL_RULE", targetId = "")
    public ResponseEntity<AiGlobalRule> createGlobalRule(
            @RequestBody CreateRuleRequest req,
            org.springframework.security.core.Authentication auth) {

        AiGlobalRule rule = AiGlobalRule.builder()
                .ruleText(req.getRuleText().trim())
                .scope(req.getScope() != null ? req.getScope() : "ALL")
                .sourceCorrectionId(null)
                .active(true)
                .createdBy(auth.getName())
                .build();
        return ResponseEntity.status(HttpStatus.CREATED).body(globalRuleRepository.save(rule));
    }

    @PatchMapping("/global/{id}")
    @Operation(summary = "전역 금지 규칙 활성화·비활성화", description = "active 값을 토글한다.")
    @Auditable(action = "AI_GLOBAL_RULE_TOGGLE", targetType = "AI_GLOBAL_RULE", targetId = "#id")
    public ResponseEntity<AiGlobalRule> toggleGlobalRule(
            @PathVariable Long id,
            @RequestBody ToggleRequest req) {

        AiGlobalRule rule = globalRuleRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "RULE_NOT_FOUND"));
        rule.setActive(req.isActive());
        return ResponseEntity.ok(globalRuleRepository.save(rule));
    }

    @DeleteMapping("/global/{id}")
    @Operation(summary = "전역 금지 규칙 삭제")
    @Auditable(action = "AI_GLOBAL_RULE_DELETE", targetType = "AI_GLOBAL_RULE", targetId = "#id")
    public ResponseEntity<Void> deleteGlobalRule(@PathVariable Long id) {
        AiGlobalRule rule = globalRuleRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "RULE_NOT_FOUND"));
        globalRuleRepository.delete(rule);
        return ResponseEntity.noContent().build();
    }

    // =====================================================================
    // 페르소나 주의사항 관리
    // =====================================================================

    @GetMapping("/cautions")
    @Operation(summary = "페르소나 주의사항 목록", description = "페르소나별 첨삭 기반 주의사항 목록을 조회한다.")
    public ResponseEntity<Page<AiContentCorrection>> listCautions(
            @RequestParam(value = "personaId", required = false) String personaId,
            Pageable pageable) {

        Page<AiContentCorrection> result = (personaId != null && !personaId.isBlank())
                ? correctionRepository.findByPersonaIdAndPersonaCautionIsNotNullOrderByCreatedAtDesc(personaId, pageable)
                : correctionRepository.findAll(pageable);
        return ResponseEntity.ok(result);
    }

    @PatchMapping("/cautions/{corrId}")
    @Operation(summary = "페르소나 주의사항 활성화·비활성화",
               description = "voice_profile.correction_cautions 배열의 해당 corr_id 항목의 active 값을 변경한다.")
    @Auditable(action = "AI_CAUTION_TOGGLE", targetType = "AI_CAUTION", targetId = "#corrId")
    public ResponseEntity<Void> toggleCaution(
            @PathVariable Long corrId,
            @RequestBody ToggleRequest req) {

        AiContentCorrection correction = correctionRepository.findById(corrId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "CORRECTION_NOT_FOUND"));

        updateCautionActiveInVoiceProfile(correction.getPersonaId(), corrId, req.isActive());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/cautions/{corrId}")
    @Operation(summary = "페르소나 주의사항 삭제",
               description = "voice_profile.correction_cautions 배열에서 해당 corr_id 항목을 제거한다.")
    @Auditable(action = "AI_CAUTION_DELETE", targetType = "AI_CAUTION", targetId = "#corrId")
    public ResponseEntity<Void> deleteCaution(@PathVariable Long corrId) {
        AiContentCorrection correction = correctionRepository.findById(corrId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "CORRECTION_NOT_FOUND"));

        removeCautionFromVoiceProfile(correction.getPersonaId(), corrId);
        return ResponseEntity.noContent().build();
    }

    // =====================================================================
    // voice_profile 갱신 헬퍼
    // =====================================================================

    private void updateCautionActiveInVoiceProfile(String personaId, long corrId, boolean active) {
        PersonaVoiceRef ref = personaVoiceRefRepository.findById(personaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "PERSONA_NOT_FOUND"));
        try {
            ObjectNode vp = parseVoiceProfile(ref.getVoiceProfile());
            if (vp.has("correction_cautions") && vp.get("correction_cautions").isArray()) {
                ArrayNode cautions = (ArrayNode) vp.get("correction_cautions");
                for (int i = 0; i < cautions.size(); i++) {
                    if (cautions.get(i).path("corr_id").asLong(-1) == corrId) {
                        ((ObjectNode) cautions.get(i)).put("active", active);
                        break;
                    }
                }
                vp.set("correction_cautions", cautions);
            }
            ref.setVoiceProfile(objectMapper.writeValueAsString(vp));
            personaVoiceRefRepository.save(ref);
        } catch (Exception e) {
            log.error("[ai-rules] caution toggle failed personaId={} corrId={}: {}", personaId, corrId, e.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "VOICE_PROFILE_UPDATE_FAILED");
        }
    }

    private void removeCautionFromVoiceProfile(String personaId, long corrId) {
        PersonaVoiceRef ref = personaVoiceRefRepository.findById(personaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "PERSONA_NOT_FOUND"));
        try {
            ObjectNode vp = parseVoiceProfile(ref.getVoiceProfile());
            if (vp.has("correction_cautions") && vp.get("correction_cautions").isArray()) {
                ArrayNode cautions = (ArrayNode) vp.get("correction_cautions");
                ArrayNode filtered = objectMapper.createArrayNode();
                for (int i = 0; i < cautions.size(); i++) {
                    if (cautions.get(i).path("corr_id").asLong(-1) != corrId) {
                        filtered.add(cautions.get(i));
                    }
                }
                vp.set("correction_cautions", filtered);
            }
            ref.setVoiceProfile(objectMapper.writeValueAsString(vp));
            personaVoiceRefRepository.save(ref);
        } catch (Exception e) {
            log.error("[ai-rules] caution delete failed personaId={} corrId={}: {}", personaId, corrId, e.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "VOICE_PROFILE_UPDATE_FAILED");
        }
    }

    private ObjectNode parseVoiceProfile(String vpJson) throws Exception {
        if (vpJson == null || vpJson.isBlank()) return objectMapper.createObjectNode();
        return (ObjectNode) objectMapper.readTree(vpJson);
    }

    // =====================================================================
    // 첨삭 이력 관리 (수정 버튼 + AI 개선 모두 집계)
    // =====================================================================

    @GetMapping("/history")
    @Operation(summary = "첨삭 이력 전체 목록", description = "관리자 수정·AI 개선으로 생성된 모든 첨삭 이력을 조회한다. status 필터 가능(PENDING/PROCESSED/SKIPPED).")
    public ResponseEntity<Page<AiContentCorrection>> listHistory(
            @RequestParam(value = "status", required = false) String status,
            Pageable pageable) {

        Page<AiContentCorrection> result = (status != null && !status.isBlank())
                ? correctionRepository.findByStatusOrderByCreatedAtDesc(status.toUpperCase(), pageable)
                : correctionRepository.findAllByOrderByCreatedAtDesc(pageable);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/history/{corrId}/analyze")
    @Operation(summary = "첨삭 LLM 분석 (Sonnet)",
               description = "PENDING 첨삭을 Sonnet으로 분석해 페르소나 주의사항·전역 규칙 초안을 반환한다. DB 미변경.")
    public ResponseEntity<AiCorrectionService.AnalyzeResult> analyzeHistory(
            @PathVariable Long corrId) throws Exception {

        AiCorrectionService.AnalyzeResult result = aiCorrectionService.analyzeById(corrId);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/history/{corrId}/apply")
    @Operation(summary = "첨삭 규칙 적용",
               description = "scope에 따라 페르소나 주의사항·전역 규칙으로 적용하고 PROCESSED로 승격한다.")
    @Auditable(action = "AI_CORRECTION_APPLY", targetType = "AI_CORRECTION", targetId = "#corrId")
    public ResponseEntity<AiCorrectionService.CommitResult> applyHistory(
            @PathVariable Long corrId,
            @RequestBody ApplyHistoryRequest req,
            org.springframework.security.core.Authentication auth) {

        AiCorrectionService.CommitResult result = aiCorrectionService.applyById(
                new AiCorrectionService.ApplyRequest(
                        corrId,
                        req.getScope(),
                        req.getPersonaCaution(),
                        req.getGlobalRules(),
                        req.isPushToBank()
                ),
                auth.getName()
        );
        return ResponseEntity.ok(result);
    }

    @PatchMapping("/history/{corrId}/skip")
    @Operation(summary = "첨삭 건너뜀", description = "PENDING 첨삭을 SKIPPED로 표시한다.")
    @Auditable(action = "AI_CORRECTION_SKIP", targetType = "AI_CORRECTION", targetId = "#corrId")
    public ResponseEntity<Void> skipHistory(@PathVariable Long corrId) {
        aiCorrectionService.skipById(corrId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/history/analyze-batch")
    @Operation(
        summary = "PENDING 첨삭 일괄 분석 시작 (map-reduce, 비동기)",
        description = "PENDING 첨삭을 청크로 분할해 Sonnet MAP + Opus REDUCE로 통합 분석. 즉시 jobId 반환(202). " +
                      "관리자가 GET /history/analyze-batch/{jobId}로 폴링 후 검토, " +
                      "POST /history/apply-batch-plan으로 승인 적용."
    )
    @Auditable(action = "AI_CORRECTION_BATCH_ANALYZE", targetType = "AI_CORRECTION", targetId = "")
    public ResponseEntity<Map<String, Object>> analyzeBatch(
            org.springframework.security.core.Authentication auth) {

        long pendingCount = correctionRepository.countByStatus("PENDING");
        if (pendingCount == 0) {
            return ResponseEntity.ok(Map.of("jobId", "", "queued", 0,
                    "message", "분석 대기 중인 첨삭이 없습니다."));
        }

        try {
            String jobId = aiBatchLearningService.startAnalysis(auth.getName());
            return ResponseEntity.accepted().body(Map.of(
                    "jobId", jobId,
                    "queued", pendingCount,
                    "message", pendingCount + "건의 첨삭 분석을 백그라운드에서 시작했습니다."
            ));
        } catch (IllegalStateException e) {
            return ResponseEntity.ok(Map.of("jobId", "", "queued", 0, "message", e.getMessage()));
        }
    }

    @GetMapping("/history/analyze-batch/{jobId}")
    @Operation(
        summary = "일괄 분석 job 상태 조회",
        description = "analyze-batch 결과를 폴링한다. status=RUNNING 이면 계속 폴링, READY면 plan 포함, FAILED면 error 포함."
    )
    public ResponseEntity<AiBatchLearningService.JobSnapshot> getBatchAnalysisJob(
            @PathVariable String jobId) {

        Optional<AiBatchLearningService.JobSnapshot> snapshot = aiBatchLearningService.getJob(jobId);
        return snapshot.map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/history/apply-batch-plan")
    @Operation(
        summary = "일괄 분석 플랜 승인 적용 (LLM 없음)",
        description = "관리자가 검토·편집한 전역 규칙 + 페르소나 주의사항을 적용하고 출처 첨삭을 PROCESSED로 승격한다."
    )
    @Auditable(action = "AI_BATCH_PLAN_APPLY", targetType = "AI_CORRECTION", targetId = "")
    public ResponseEntity<AiCorrectionService.ConsolidatedApplyResult> applyBatchPlan(
            @RequestBody AiBatchLearningService.ApplyBatchRequest req,
            org.springframework.security.core.Authentication auth) {

        AiCorrectionService.ConsolidatedApplyResult result =
                aiBatchLearningService.applyPlan(req, auth.getName());
        return ResponseEntity.ok(result);
    }

    // =====================================================================
    // Request DTOs
    // =====================================================================

    // =====================================================================
    // 기본 프롬프트 템플릿 관리 (voice/post, voice/comment, voice/reply, voice/partner)
    // =====================================================================

    @GetMapping("/prompts")
    @Operation(summary = "AI 유저 기본 프롬프트 목록", description = "voice/* 프롬프트 템플릿 전체 목록을 반환한다.")
    public ResponseEntity<List<AiPromptTemplate>> listPromptTemplates() {
        return ResponseEntity.ok(promptTemplateRepository.findAllByOrderByKeyAsc());
    }

    @GetMapping("/prompts/{category}/{name}")
    @Operation(summary = "AI 유저 기본 프롬프트 단건 조회")
    public ResponseEntity<AiPromptTemplate> getPromptTemplate(
            @PathVariable String category,
            @PathVariable String name) {
        String key = category + "/" + name;
        AiPromptTemplate tpl = promptTemplateRepository.findById(key)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "PROMPT_NOT_FOUND"));
        return ResponseEntity.ok(tpl);
    }

    @PutMapping("/prompts/{category}/{name}")
    @Operation(summary = "AI 유저 기본 프롬프트 수정", description = "내용을 저장한다. orchestrator가 5분 내 반영한다.")
    @Auditable(action = "AI_PROMPT_UPDATE", targetType = "AI_PROMPT", targetId = "#category + '/' + #name")
    public ResponseEntity<AiPromptTemplate> updatePromptTemplate(
            @PathVariable String category,
            @PathVariable String name,
            @RequestBody UpdatePromptRequest req,
            org.springframework.security.core.Authentication auth) {
        String key = category + "/" + name;

        AiPromptTemplate tpl = promptTemplateRepository.findById(key)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "PROMPT_NOT_FOUND"));
        tpl.setContent(req.getContent() != null ? req.getContent() : "");
        tpl.setUpdatedAt(Instant.now());
        tpl.setUpdatedBy(auth.getName());
        promptTemplateRepository.save(tpl);

        return ResponseEntity.ok(tpl);
    }

    // =====================================================================
    // Request DTOs
    // =====================================================================

    @Getter @Setter
    public static class ApplyHistoryRequest {
        /** "PERSONA" | "GLOBAL" | "BOTH" */
        private String scope;
        private String personaCaution;
        private List<String> globalRules;
        private boolean pushToBank = true;
    }

    @Getter @Setter
    public static class CreateRuleRequest {
        private String ruleText;
        /** 'POST' | 'COMMENT' | 'ALL'. 기본값 'ALL'. */
        private String scope;
    }

    @Getter @Setter
    public static class ToggleRequest {
        private boolean active;
    }

    @Getter @Setter
    public static class UpdatePromptRequest {
        private String content;
    }

}
