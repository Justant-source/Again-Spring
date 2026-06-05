package com.againspring.api.admin;

import com.againspring.annotation.Auditable;
import com.againspring.domain.ai.AiContentCorrection;
import com.againspring.domain.ai.AiGlobalRule;
import com.againspring.domain.ai.PersonaVoiceRef;
import com.againspring.repository.ai.AiContentCorrectionRepository;
import com.againspring.repository.ai.AiGlobalRuleRepository;
import com.againspring.repository.ai.PersonaVoiceRefRepository;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

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
    private final ObjectMapper objectMapper;

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
    // Request DTOs
    // =====================================================================

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
}
