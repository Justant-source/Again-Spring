package com.againspring.api.admin;

import com.againspring.annotation.Auditable;
import com.againspring.service.ai.AiCorrectionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * AI 첨삭 학습 API (ADMIN 전용).
 *
 * 2단계 플로우:
 * 1. POST /analyze  — 원본↔수정본 diff LLM 분석 → 초안 반환 (DB 미변경)
 * 2. POST /commit   — 관리자 확인 후 영속화 → 라이브 교체 + 규칙 누적 + example_bank 환류
 */
@RestController
@RequestMapping("/api/admin/content/corrections")
@RequiredArgsConstructor
@Tag(name = "Admin — AI Correction", description = "AI 작성 글/댓글 첨삭 학습 (ADMIN 전용)")
@SecurityRequirement(name = "bearer-jwt")
@PreAuthorize("hasRole('ADMIN')")
public class AdminCorrectionController {

    private final AiCorrectionService correctionService;

    // =====================================================================
    // 즉시 저장 (LLM 없음 — PENDING 상태로 쌓기)
    // =====================================================================

    @PostMapping("/save")
    @Operation(
        summary = "첨삭 즉시 저장 (LLM 없음)",
        description = "수정본을 PENDING 상태로 즉시 저장. LLM 호출 없이 반환. applyLive=true 시 라이브 본문도 즉시 교체."
    )
    public ResponseEntity<SaveResponse> save(
            @RequestBody SaveRequest req,
            org.springframework.security.core.Authentication auth) {

        String adminId = auth.getName();
        AiCorrectionService.SaveResult result = correctionService.savePending(
                req.getTargetType(), req.getTargetId(), req.getCorrectedText(),
                req.isApplyLive(), adminId);

        return ResponseEntity.ok(new SaveResponse(result.correctionId(), result.appliedLive()));
    }

    // =====================================================================
    // 단계 A: 분석 (DB 미변경)
    // =====================================================================

    @PostMapping("/analyze")
    @Operation(
        summary = "첨삭 분석",
        description = "AI 원본↔관리자 수정본 차이를 LLM으로 분석, 페르소나 주의사항·전역 규칙 초안 반환. DB 저장 없음."
    )
    public ResponseEntity<AnalyzeResponse> analyze(
            @RequestBody AnalyzeRequest req,
            org.springframework.security.core.Authentication auth) throws Exception {

        String adminId = auth.getName();
        AiCorrectionService.AnalyzeResult result = correctionService.analyze(
                new AiCorrectionService.AnalyzeRequest(req.getTargetType(), req.getTargetId(), req.getCorrectedText()),
                adminId);

        return ResponseEntity.ok(new AnalyzeResponse(
                result.personaId(),
                result.originalText(),
                result.suggestedCaution(),
                result.suggestedGlobalRules()));
    }

    // =====================================================================
    // 단계 B: 확정 (트랜잭션)
    // =====================================================================

    @PostMapping("/commit")
    @Operation(
        summary = "첨삭 확정",
        description = "관리자가 검토·편집한 첨삭 결과를 영속화. 라이브 본문 교체 + 페르소나 주의사항 갱신 + 전역 규칙 누적 + example_bank 환류."
    )
    @Auditable(action = "AI_CORRECTION_COMMIT", targetType = "#req.targetType", targetId = "#req.targetId")
    public ResponseEntity<CommitResponse> commit(
            @RequestBody CommitRequest req,
            org.springframework.security.core.Authentication auth) {

        String adminId = auth.getName();
        AiCorrectionService.CommitResult result = correctionService.commit(
                new AiCorrectionService.CommitRequest(
                        req.getTargetType(), req.getTargetId(), req.getCorrectedText(),
                        req.getPersonaCaution(), req.getGlobalRules(), req.isApplyLive()),
                adminId);

        return ResponseEntity.ok(new CommitResponse(
                result.correctionId(), result.appliedLive(),
                result.rulesCreated(), result.cautionApplied()));
    }

    // =====================================================================
    // Request / Response DTOs
    // =====================================================================

    @Getter @Setter
    public static class SaveRequest {
        private String targetType;
        private String targetId;
        private String correctedText;
        private boolean applyLive;
    }

    @Getter
    public static class SaveResponse {
        private final long correctionId;
        private final boolean appliedLive;

        public SaveResponse(long correctionId, boolean appliedLive) {
            this.correctionId = correctionId;
            this.appliedLive = appliedLive;
        }
    }

    @Getter @Setter
    public static class AnalyzeRequest {
        /** 'POST' | 'COMMENT' */
        private String targetType;
        /** post.id 또는 comment.id 문자열 */
        private String targetId;
        /** 관리자가 수정한 본문 */
        private String correctedText;
    }

    @Getter
    public static class AnalyzeResponse {
        private final String personaId;
        private final String originalText;
        private final String suggestedCaution;
        private final List<String> suggestedGlobalRules;

        public AnalyzeResponse(String personaId, String originalText,
                               String suggestedCaution, List<String> suggestedGlobalRules) {
            this.personaId = personaId;
            this.originalText = originalText;
            this.suggestedCaution = suggestedCaution;
            this.suggestedGlobalRules = suggestedGlobalRules;
        }
    }

    @Getter @Setter
    public static class CommitRequest {
        private String targetType;
        private String targetId;
        private String correctedText;
        /** 페르소나 주의사항 (편집 가능, nullable) */
        private String personaCaution;
        /** 선택·편집된 전역 금지 규칙 목록 */
        private List<String> globalRules;
        /** true면 라이브 본문도 수정본으로 교체 */
        private boolean applyLive;
    }

    @Getter
    public static class CommitResponse {
        private final long correctionId;
        private final boolean appliedLive;
        private final int rulesCreated;
        private final boolean cautionApplied;

        public CommitResponse(long correctionId, boolean appliedLive, int rulesCreated, boolean cautionApplied) {
            this.correctionId = correctionId;
            this.appliedLive = appliedLive;
            this.rulesCreated = rulesCreated;
            this.cautionApplied = cautionApplied;
        }
    }
}
