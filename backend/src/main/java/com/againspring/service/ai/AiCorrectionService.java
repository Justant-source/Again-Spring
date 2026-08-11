package com.againspring.service.ai;

import com.againspring.domain.ai.AiContentCorrection;
import com.againspring.domain.ai.AiGlobalRule;
import com.againspring.domain.ai.PersonaVoiceRef;
import com.againspring.domain.community.Post;
import com.againspring.domain.community.PostComment;
import com.againspring.llm.LLMProvider;
import com.againspring.llm.PromptSanitizer;
import com.againspring.repository.ai.AiContentCorrectionRepository;
import com.againspring.repository.ai.AiGlobalRuleRepository;
import com.againspring.repository.ai.PersonaVoiceRefRepository;
import com.againspring.repository.community.PostCommentRepository;
import com.againspring.repository.community.PostRepository;
import com.againspring.service.community.PostSearchNgramIndexer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

/**
 * AI 첨삭 학습 핵심 서비스.
 * 분석(analyze)과 확정(commit) 2단계로 동작한다.
 *
 * [흐름]
 * 1. analyze: 원본↔수정본 diff → RemoteLlmProvider로 분석 → 주의사항/전역규칙 초안 반환 (DB 미변경)
 * 2. commit : 관리자가 확인/편집한 결과를 영속화 → 라이브 본문 교체 → voice_profile 갱신 → example_bank 환류
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiCorrectionService {

    private final PostRepository postRepository;
    private final PostCommentRepository postCommentRepository;
    private final AiContentCorrectionRepository correctionRepository;
    private final AiGlobalRuleRepository globalRuleRepository;
    private final PersonaVoiceRefRepository personaVoiceRefRepository;
    private final AiLearningBridge aiLearningBridge;
    private final PostSearchNgramIndexer postSearchNgramIndexer;
    private final PromptSanitizer promptSanitizer;
    private final ObjectMapper objectMapper;

    @Qualifier("remoteLlmProvider")
    private final LLMProvider llmProvider;

    /** 첨삭 분석 전용 모델 — Sonnet (MAP 단계: 청크별 패턴 추출) */
    @Value("${llm.correction.model:claude-sonnet-4-6}")
    private String correctionModel;

    /** correction_cautions 최대 보관 수 */
    private static final int MAX_CAUTIONS = 6;

    // =====================================================================
    // 분석 단계 (DB 미변경)
    // =====================================================================

    public record AnalyzeRequest(String targetType, String targetId, String correctedText) {}

    public record AnalyzeResult(
        String personaId,
        String originalText,
        String suggestedCaution,
        List<String> suggestedGlobalRules
    ) {}

    /**
     * 원본↔수정본 차이를 LLM으로 분석, 관리자 검토용 초안 반환.
     * DB 저장 없음.
     */
    public AnalyzeResult analyze(AnalyzeRequest req, String adminId) throws Exception {
        String originalText = fetchOriginalText(req.targetType(), req.targetId());
        String personaId    = fetchPersonaId(req.targetType(), req.targetId());

        String diffPrompt = buildDiffPrompt(originalText, req.correctedText(), null);
        String llmResponse = llmProvider.invoke(diffPrompt, correctionModel);

        return parseAnalyzeResponse(llmResponse, personaId, originalText);
    }

    // =====================================================================
    // 확정 단계 (트랜잭션)
    // =====================================================================

    public record CommitRequest(
        String targetType,
        String targetId,
        String correctedText,
        String personaCaution,       // nullable — 빈 문자열이면 무시
        List<String> globalRules,    // 빈 리스트 허용
        boolean applyLive
    ) {}

    public record CommitResult(
        long correctionId,
        boolean appliedLive,
        int rulesCreated,
        boolean cautionApplied
    ) {}

    @Transactional
    public CommitResult commit(CommitRequest req, String adminId) {
        String originalText = fetchOriginalText(req.targetType(), req.targetId());
        String personaId    = fetchPersonaId(req.targetType(), req.targetId());
        String category     = fetchCategory(req.targetType(), req.targetId());

        // 1) 첨삭 기록 저장
        String caution = (req.personaCaution() != null && !req.personaCaution().isBlank())
                ? req.personaCaution().trim() : null;

        AiContentCorrection correction = AiContentCorrection.builder()
                .targetType(req.targetType())
                .targetId(req.targetId())
                .personaId(personaId)
                .category(category)
                .originalText(originalText)
                .correctedText(req.correctedText())
                .personaCaution(caution)
                .adminId(adminId)
                .status("PROCESSED")
                .appliedLive(false)
                .pushedToBank(false)
                .build();
        correction = correctionRepository.save(correction);
        long correctionId = correction.getId();

        // 2) 라이브 본문 교체
        boolean appliedLive = false;
        if (req.applyLive()) {
            applyLiveCorrection(req.targetType(), req.targetId(), req.correctedText());
            correction.setAppliedLive(true);
            appliedLive = true;
        }

        // 3) 페르소나 voice_profile.correction_cautions 갱신
        boolean cautionApplied = false;
        if (caution != null) {
            mergePersonaCaution(personaId, caution, correctionId);
            cautionApplied = true;
        }

        // 4) 전역 금지 규칙 저장
        int rulesCreated = 0;
        if (req.globalRules() != null) {
            for (String rule : req.globalRules()) {
                if (rule == null || rule.isBlank()) continue;
                AiGlobalRule globalRule = AiGlobalRule.builder()
                        .ruleText(rule.trim())
                        .scope("ALL")
                        .sourceCorrectionId(correctionId)
                        .active(true)
                        .createdBy(adminId)
                        .build();
                globalRuleRepository.save(globalRule);
                rulesCreated++;
            }
        }

        // 5) example_bank 환류 (비동기 — 실패해도 트랜잭션에 영향 없음)
        String contentType = "POST".equals(req.targetType()) ? "POST" : "COMMENT";
        try {
            aiLearningBridge.saveCorrectedAsync(req.correctedText(), contentType, category, 1.0);
            correction.setPushedToBank(true);
        } catch (Exception e) {
            log.warn("[ai-correction] example_bank 환류 실패 (non-critical): {}", e.getMessage());
        }

        correctionRepository.save(correction);

        log.info("[ai-correction] commit complete correctionId={} persona={} caution={} rules={} live={}",
                correctionId, personaId, cautionApplied, rulesCreated, appliedLive);

        return new CommitResult(correctionId, appliedLive, rulesCreated, cautionApplied);
    }

    // =====================================================================
    // voice_profile 주의사항 머지
    // =====================================================================

    /**
     * personas.voice_profile.correction_cautions 배열에 새 주의사항을 prepend.
     * 최대 MAX_CAUTIONS 개 유지. UTC-8/ensure_ascii=False 동형 — UTF-8 Jackson 직렬화.
     */
    private void mergePersonaCaution(String personaId, String cautionText, long correctionId) {
        PersonaVoiceRef ref = personaVoiceRefRepository.findById(personaId).orElse(null);
        if (ref == null) {
            log.warn("[ai-correction] persona {} not found in personas table, skipping caution merge", personaId);
            return;
        }
        try {
            String vpJson = ref.getVoiceProfile();
            ObjectNode vp = vpJson != null && !vpJson.isBlank()
                    ? (ObjectNode) objectMapper.readTree(vpJson)
                    : objectMapper.createObjectNode();

            // correction_cautions 배열 읽기 또는 생성
            ArrayNode cautions;
            if (vp.has("correction_cautions") && vp.get("correction_cautions").isArray()) {
                cautions = (ArrayNode) vp.get("correction_cautions");
            } else {
                cautions = objectMapper.createArrayNode();
                vp.set("correction_cautions", cautions);
            }

            // 새 항목 prepend
            ObjectNode newEntry = objectMapper.createObjectNode();
            newEntry.put("text", cautionText);
            newEntry.put("corr_id", correctionId);
            newEntry.put("active", true);

            // 기존 목록 복사 후 앞에 추가, MAX_CAUTIONS 컷
            ArrayNode merged = objectMapper.createArrayNode();
            merged.add(newEntry);
            for (int i = 0; i < cautions.size() && merged.size() < MAX_CAUTIONS; i++) {
                merged.add(cautions.get(i));
            }
            vp.set("correction_cautions", merged);

            ref.setVoiceProfile(objectMapper.writeValueAsString(vp));
            personaVoiceRefRepository.save(ref);
            log.debug("[ai-correction] merged caution for persona {} corr_id={}", personaId, correctionId);
        } catch (Exception e) {
            log.error("[ai-correction] voice_profile merge failed for persona {}: {}", personaId, e.getMessage());
            // 주의사항 머지 실패는 firstFailFast하지 않음 — 기록 자체는 이미 저장됨
        }
    }

    // =====================================================================
    // 헬퍼
    // =====================================================================

    private String fetchOriginalText(String targetType, String targetId) {
        if ("POST".equals(targetType)) {
            Post post = postRepository.findById(targetId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "POST_NOT_FOUND"));
            return post.getBodyPublished() != null ? post.getBodyPublished() : post.getBodyRaw();
        } else {
            PostComment comment = postCommentRepository.findById(Long.parseLong(targetId))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "COMMENT_NOT_FOUND"));
            return comment.getBody();
        }
    }

    private String fetchPersonaId(String targetType, String targetId) {
        String authorId;
        if ("POST".equals(targetType)) {
            Post post = postRepository.findById(targetId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "POST_NOT_FOUND"));
            authorId = post.getAuthorId();
        } else {
            PostComment comment = postCommentRepository.findById(Long.parseLong(targetId))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "COMMENT_NOT_FOUND"));
            authorId = comment.getAuthorId();
        }
        return authorId;
    }

    private String fetchCategory(String targetType, String targetId) {
        if ("POST".equals(targetType)) {
            return postRepository.findById(targetId)
                    .map(p -> p.getCategory() != null ? p.getCategory().name() : null)
                    .orElse(null);
        }
        // 댓글은 카테고리 없음 (postId로 역참조 가능하나 MVP에서는 null 허용)
        return null;
    }

    private String buildDiffPrompt(String originalText, String correctedText, String adminOpinion) {
        // 사용자 입력은 PromptSanitizer 경유 + <user_input> 태그 (CLAUDE.md 보안 규칙)
        String safeOriginal   = promptSanitizer.sanitize(originalText);
        String safeCorrected  = promptSanitizer.sanitize(correctedText);

        String opinionBlock = "";
        if (adminOpinion != null && !adminOpinion.isBlank()) {
            String safeOpinion = promptSanitizer.sanitize(adminOpinion);
            opinionBlock = "\n[관리자 의견]\n" + safeOpinion + "\n";
        }

        return """
다음은 AI 유저가 한국 커뮤니티에 작성한 글(원본)과 관리자가 수정한 버전(수정본)입니다.
두 텍스트의 차이를 분석하여 아래 JSON 형식으로 응답하세요.

<user_input>
[원본]
%s

[수정본]
%s
%s
</user_input>

분석 지침:
- 원본과 수정본의 차이(삭제·추가·변경)를 파악하세요.
- 관리자 의견이 있다면 수정 의도의 핵심 신호로 활용하세요.
- 이 AI 작성자가 다음 글을 쓸 때 반드시 주의해야 할 사항 1문장을 생성하세요 (persona_caution).
  - 구체적이어야 하며 "~하지 말 것" 또는 "~에 주의할 것" 형태.
- 수정이 시사하는 일반 규칙(모든 AI 유저에게 공통 적용할 수 있는 것) 0~3개를 생성하세요 (global_rules).
  - 없으면 빈 배열.
  - 각 항목은 "~하지 말 것" 형식 단문.

반드시 아래 JSON만 반환하세요 (마크다운 코드블록 없이):
{
  "persona_caution": "...",
  "global_rules": ["...", "..."]
}
""".formatted(safeOriginal, safeCorrected, opinionBlock);
    }

    // =====================================================================
    // 재구성 첨삭 (원본 비교 기능)
    // =====================================================================

    /**
     * 재구성 분석 요청 — 원본 커뮤니티 글, AI 생성본, 관리자 수정본 3-way diff.
     * DB 저장 없음 (관리자 검토용 초안 반환).
     */
    public record ReconstructionAnalyzeRequest(
        String targetType,          // "POST"
        String targetId,
        String sourceOriginalText,  // 크롤 원본
        String correctedText,       // 관리자 수정본
        String adminOpinion
    ) {}

    public record ReconstructionAnalyzeResult(
        String personaId,
        String generatedText,       // AI 생성본 (BE에서 가져옴)
        List<String> suggestedReconstructionRules
    ) {}

    /**
     * 3-way diff (원본/AI생성/관리자수정) → Sonnet → 재구성 규칙 초안.
     */
    public ReconstructionAnalyzeResult analyzeReconstruction(ReconstructionAnalyzeRequest req, String adminId) throws Exception {
        String generatedText = fetchOriginalText(req.targetType(), req.targetId());
        String personaId     = fetchPersonaId(req.targetType(), req.targetId());
        String prompt = buildReconstructionPrompt(req.sourceOriginalText(), generatedText, req.correctedText(), req.adminOpinion());
        String llmResponse = llmProvider.invoke(prompt, correctionModel);
        return parseReconstructionAnalyzeResponse(llmResponse, personaId, generatedText);
    }

    /**
     * 재구성 커밋 — scope=RECONSTRUCTION 전역 규칙 저장.
     * 기존 POST/COMMENT commit과 분리되어 scope 혼입 없음.
     */
    public record ReconstructionCommitRequest(
        String targetType,
        String targetId,
        String correctedText,
        String sourceOriginalText,
        List<String> reconstructionRules,
        boolean applyLive
    ) {}

    @Transactional
    public CommitResult commitReconstruction(ReconstructionCommitRequest req, String adminId) {
        String generatedText = fetchOriginalText(req.targetType(), req.targetId());
        String personaId     = fetchPersonaId(req.targetType(), req.targetId());
        String category      = fetchCategory(req.targetType(), req.targetId());

        // 1) 첨삭 기록 저장 (sourceOriginalText 포함 — 재구성 표시)
        AiContentCorrection correction = AiContentCorrection.builder()
                .targetType(req.targetType())
                .targetId(req.targetId())
                .personaId(personaId)
                .category(category)
                .originalText(generatedText)
                .correctedText(req.correctedText())
                .sourceOriginalText(req.sourceOriginalText())
                .adminId(adminId)
                .status("PROCESSED")
                .appliedLive(false)
                .pushedToBank(false)
                .build();
        correction = correctionRepository.save(correction);
        long correctionId = correction.getId();

        // 2) 라이브 본문 교체
        boolean appliedLive = false;
        if (req.applyLive()) {
            applyLiveCorrection(req.targetType(), req.targetId(), req.correctedText());
            correction.setAppliedLive(true);
            appliedLive = true;
        }

        // 3) 전역 재구성 규칙 저장 (scope=RECONSTRUCTION)
        int rulesCreated = 0;
        if (req.reconstructionRules() != null) {
            for (String rule : req.reconstructionRules()) {
                if (rule == null || rule.isBlank()) continue;
                AiGlobalRule globalRule = AiGlobalRule.builder()
                        .ruleText(rule.trim())
                        .scope("RECONSTRUCTION")
                        .sourceCorrectionId(correctionId)
                        .active(true)
                        .createdBy(adminId)
                        .build();
                globalRuleRepository.save(globalRule);
                rulesCreated++;
            }
        }

        // 4) example_bank 환류 (교정본)
        String contentType = "POST".equals(req.targetType()) ? "POST" : "COMMENT";
        try {
            aiLearningBridge.saveCorrectedAsync(req.correctedText(), contentType, category, 1.0);
            correction.setPushedToBank(true);
        } catch (Exception e) {
            log.warn("[ai-reconstruction] example_bank 환류 실패 (non-critical): {}", e.getMessage());
        }

        correctionRepository.save(correction);
        log.info("[ai-reconstruction] commit complete correctionId={} persona={} rules={} live={}",
                correctionId, personaId, rulesCreated, appliedLive);

        return new CommitResult(correctionId, appliedLive, rulesCreated, false);
    }

    private String buildReconstructionPrompt(String sourceOriginal, String generatedText,
                                              String correctedText, String adminOpinion) {
        // 사용자 입력 전부 PromptSanitizer 경유 + <user_input> 태그 (CLAUDE.md 보안 규칙)
        String safeSource    = promptSanitizer.sanitize(sourceOriginal != null ? sourceOriginal : "");
        String safeGenerated = promptSanitizer.sanitize(generatedText);
        String safeCorrected = promptSanitizer.sanitize(correctedText);

        String opinionBlock = "";
        if (adminOpinion != null && !adminOpinion.isBlank()) {
            opinionBlock = "\n[관리자 의견]\n" + promptSanitizer.sanitize(adminOpinion) + "\n";
        }

        return """
다음은 외부 커뮤니티 원본 글, AI가 이를 재구성한 사연, 관리자가 수정한 최종본입니다.
세 텍스트의 차이를 분석하여 아래 JSON 형식으로 응답하세요.

<user_input>
[원본 커뮤니티 글]
%s

[AI 재구성본]
%s

[관리자 수정본]
%s
%s
</user_input>

분석 지침:
- 원본 → AI 재구성본 → 관리자 수정본으로 이어지는 변화를 파악하세요.
- 관리자가 AI 재구성본에서 무엇을 고쳤는지, 원본의 어떤 요소가 잘못 변환되었는지 분석하세요.
- 이 패턴을 바탕으로 "원본 커뮤니티 글을 다시봄 사연으로 재구성할 때 지켜야 할 규칙" 1~3개를 생성하세요 (reconstruction_rules).
  - 구체적이고 일반화 가능해야 합니다 ("~로 유지할 것", "~를 변환할 것", "~를 생략하지 말 것" 형태).
  - 없으면 빈 배열.

반드시 아래 JSON만 반환하세요 (마크다운 코드블록 없이):
{
  "reconstruction_rules": ["...", "..."]
}
""".formatted(safeSource, safeGenerated, safeCorrected, opinionBlock);
    }

    private ReconstructionAnalyzeResult parseReconstructionAnalyzeResponse(
            String llmResponse, String personaId, String generatedText) throws Exception {
        String jsonStr = llmResponse.trim();
        if (jsonStr.startsWith("```")) {
            jsonStr = jsonStr.replaceAll("```[a-z]*\\n?", "").replaceAll("```", "").trim();
        }
        JsonNode node = objectMapper.readTree(jsonStr);
        List<String> rules = new ArrayList<>();
        JsonNode rulesNode = node.get("reconstruction_rules");
        if (rulesNode != null && rulesNode.isArray()) {
            for (JsonNode r : rulesNode) {
                String text = r.asText("").trim();
                if (!text.isBlank()) rules.add(text);
            }
        }
        return new ReconstructionAnalyzeResult(personaId, generatedText, rules);
    }

    // =====================================================================
    // 일괄 분석 통합 플랜 적용 (AiBatchLearningService에서 호출)
    // =====================================================================

    /**
     * map-reduce 일괄 분석 결과(통합 플랜)를 DB에 영속화한다.
     * - 전역 규칙: ai_global_rules에 저장
     * - 페르소나 주의사항: voice_profile.correction_cautions에 머지
     * - 출처 첨삭 레코드: status = PROCESSED로 승격
     * - pushToBank=true: 교정본을 example_bank로 환류
     * DB 쓰기 일원화 — AiBatchLearningService는 이 메서드만 호출한다.
     */
    @Transactional
    public ConsolidatedApplyResult applyConsolidatedPlan(
            List<GlobalRuleItem> globalRules,
            List<PersonaCautionItem> personaCautions,
            List<Long> allSourceCorrIds,
            boolean pushToBank,
            String adminId) {

        int rulesCreated = 0;
        int cautionsApplied = 0;
        int corrProcessed = 0;

        // 1) 전역 규칙 저장 (오류 시그니처 검증 포함)
        for (GlobalRuleItem item : globalRules) {
            if (item.ruleText() == null || item.ruleText().isBlank()) continue;
            if (isErrorSignature(item.ruleText())) {
                log.warn("[ai-correction] 배치 플랜: 오류 시그니처 감지 → 전역 규칙 저장 건너뜀: {}", item.ruleText().substring(0, Math.min(40, item.ruleText().length())));
                continue;
            }
            Long sourceCorrId = (item.sourceCorrIds() != null && !item.sourceCorrIds().isEmpty())
                    ? item.sourceCorrIds().get(0) : null;
            AiGlobalRule rule = AiGlobalRule.builder()
                    .ruleText(item.ruleText().trim())
                    .scope(item.scope() != null ? item.scope() : "ALL")
                    .sourceCorrectionId(sourceCorrId)
                    .active(true)
                    .createdBy(adminId)
                    .build();
            globalRuleRepository.save(rule);
            rulesCreated++;
        }

        // 2) 페르소나 주의사항 머지 (오류 시그니처 검증 포함)
        for (PersonaCautionItem item : personaCautions) {
            if (item.cautionText() == null || item.cautionText().isBlank()) continue;
            if (isErrorSignature(item.cautionText())) {
                log.warn("[ai-correction] 배치 플랜: 오류 시그니처 감지 → 주의사항 저장 건너뜀: {}", item.cautionText().substring(0, Math.min(40, item.cautionText().length())));
                continue;
            }
            Long sourceCorrId = (item.sourceCorrIds() != null && !item.sourceCorrIds().isEmpty())
                    ? item.sourceCorrIds().get(0) : null;
            mergePersonaCaution(item.personaId(), item.cautionText().trim(),
                    sourceCorrId != null ? sourceCorrId : -1L);
            cautionsApplied++;
        }

        // 3) 출처 첨삭 레코드 PROCESSED 승격 + example_bank 환류
        if (allSourceCorrIds != null) {
            for (Long corrId : allSourceCorrIds) {
                correctionRepository.findById(corrId).ifPresent(correction -> {
                    correction.setStatus("PROCESSED");

                    if (pushToBank) {
                        try {
                            aiLearningBridge.saveCorrectedAsync(
                                    correction.getCorrectedText(),
                                    "POST".equals(correction.getTargetType()) ? "POST" : "COMMENT",
                                    correction.getCategory(), 1.0);
                            correction.setPushedToBank(true);
                        } catch (Exception e) {
                            log.warn("[ai-correction] batch bank push failed correctionId={}: {}", corrId, e.getMessage());
                        }
                    }

                    correctionRepository.save(correction);
                });
            }
            corrProcessed = allSourceCorrIds.size();
        }

        log.info("[ai-correction] applyConsolidatedPlan complete — rules={} cautions={} corrections={}",
                rulesCreated, cautionsApplied, corrProcessed);

        return new ConsolidatedApplyResult(rulesCreated, cautionsApplied, corrProcessed);
    }

    /** 텍스트가 LLM 제공자 오류 시그니처를 포함하는지 확인 (CLAUDE.md 규칙 #7) */
    public static boolean isErrorSignature(String text) {
        if (text == null || text.isBlank()) return false;
        String t = text.toLowerCase();
        return t.contains("credit balance") || t.contains("too low to access")
                || t.contains("usage limit") || t.contains("rate limit")
                || t.contains("rate_limit") || t.contains("overloaded")
                || t.contains("invalid_request_error") || t.contains("authentication_error")
                || t.contains("api_error") || t.contains("anthropic api")
                || t.contains("too many requests") || t.contains("service unavailable")
                || t.contains("internal server error") || t.contains("purchase credits")
                || t.contains("insufficient credit");
    }

    // =====================================================================
    // 배치 학습 플랜 관련 타입
    // =====================================================================

    public record GlobalRuleItem(String ruleText, String scope, List<Long> sourceCorrIds) {}

    public record PersonaCautionItem(String personaId, String cautionText, List<Long> sourceCorrIds) {}

    public record ConsolidatedApplyResult(int rulesCreated, int cautionsApplied, int corrProcessed) {}

    private AnalyzeResult parseAnalyzeResponse(String llmResponse, String personaId, String originalText) {
        try {
            JsonNode root = parseJsonFromLlm(llmResponse);
            String suggestedCaution = root.path("persona_caution").asText("").trim();
            List<String> suggestedRules = new ArrayList<>();
            JsonNode rulesNode = root.path("global_rules");
            if (rulesNode.isArray()) {
                for (JsonNode r : rulesNode) {
                    String rule = r.asText("").trim();
                    if (!rule.isBlank()) suggestedRules.add(rule);
                }
            }
            return new AnalyzeResult(personaId, originalText,
                    suggestedCaution.isBlank() ? null : suggestedCaution,
                    suggestedRules);
        } catch (Exception e) {
            log.warn("[ai-correction] LLM 응답 파싱 실패, 빈 초안 반환: {}", e.getMessage());
            return new AnalyzeResult(personaId, originalText, null, List.of());
        }
    }

    /** JSON extract pattern */
    private JsonNode parseJsonFromLlm(String response) throws Exception {
        String json = response;
        if (json.contains("```json")) {
            int start = json.indexOf("```json") + 7;
            int end   = json.lastIndexOf("```");
            if (end > start) {
                return objectMapper.readTree(json.substring(start, end).trim());
            }
        }
        if (json.contains("```")) {
            int start = json.indexOf("```") + 3;
            int end   = json.lastIndexOf("```");
            if (end > start) {
                String candidate = json.substring(start, end).trim();
                if (candidate.startsWith("{")) return objectMapper.readTree(candidate);
            }
        }
        int braceStart = json.indexOf('{');
        int braceEnd   = json.lastIndexOf('}');
        if (braceStart >= 0 && braceEnd > braceStart) {
            return objectMapper.readTree(json.substring(braceStart, braceEnd + 1));
        }
        return objectMapper.readTree(json.trim());
    }

    private void applyLiveCorrection(String targetType, String targetId, String correctedText) {
        if ("POST".equals(targetType)) {
            Post post = postRepository.findById(targetId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "POST_NOT_FOUND"));
            post.setBodyPublished(correctedText);
            postRepository.save(post);
            postSearchNgramIndexer.reindex(post);
        } else {
            PostComment comment = postCommentRepository.findById(Long.parseLong(targetId))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "COMMENT_NOT_FOUND"));
            comment.setBody(correctedText);
            postCommentRepository.save(comment);
        }
    }

    // =====================================================================
    // AI 개선 다이얼로그 — LLM 없이 즉시 PENDING 저장
    // =====================================================================

    public record SaveResult(long correctionId, boolean appliedLive) {}

    /**
     * 원본↔수정본을 PENDING 상태로 즉시 저장 (LLM 호출 없음).
     * applyLive=true 이면 라이브 본문도 즉시 교체.
     * 변경이 없으면 correctionId=-1 반환.
     * adminOpinion: 관리자가 입력한 수정 의도/방향 (선택, null 허용).
     */
    @Transactional
    public SaveResult savePending(String targetType, String targetId,
                                  String correctedText, boolean applyLive,
                                  String adminId, String adminOpinion) {
        String originalText = fetchOriginalText(targetType, targetId);
        if (originalText.equals(correctedText)) {
            return new SaveResult(-1, false);
        }

        String personaId = fetchPersonaId(targetType, targetId);
        String category  = fetchCategory(targetType, targetId);

        String opinion = (adminOpinion != null && !adminOpinion.isBlank())
                ? adminOpinion.trim() : null;

        AiContentCorrection correction = AiContentCorrection.builder()
                .targetType(targetType)
                .targetId(targetId)
                .personaId(personaId)
                .category(category)
                .originalText(originalText)
                .correctedText(correctedText)
                .adminOpinion(opinion)
                .adminId(adminId)
                .status("PENDING")
                .appliedLive(applyLive)
                .pushedToBank(false)
                .build();
        correction = correctionRepository.save(correction);

        if (applyLive) {
            applyLiveCorrection(targetType, targetId, correctedText);
        }

        log.info("[ai-correction] savePending correctionId={} targetType={} applyLive={}",
                correction.getId(), targetType, applyLive);
        return new SaveResult(correction.getId(), applyLive);
    }

    // =====================================================================
    // 일괄 비동기 분석 (PENDING → PROCESSED)
    // =====================================================================

    /**
     * 모든 PENDING 첨삭을 비동기로 LLM 분석 후 자동 적용.
     * scope=BOTH, pushToBank=true 로 자동 처리.
     * HTTP 응답은 즉시 반환하고 백그라운드에서 실행.
     */
    @Async("taskExecutor")
    public void analyzePendingBatchAsync(String adminId) {
        List<AiContentCorrection> pending = correctionRepository.findByStatus("PENDING");
        log.info("[ai-correction] batch start — {} pending corrections", pending.size());

        int processed = 0;
        for (AiContentCorrection correction : pending) {
            try {
                String diffPrompt = buildDiffPrompt(correction.getOriginalText(), correction.getCorrectedText(), correction.getAdminOpinion());
                String llmResponse = llmProvider.invoke(diffPrompt, correctionModel);
                AnalyzeResult result = parseAnalyzeResponse(llmResponse, correction.getPersonaId(), correction.getOriginalText());

                // 페르소나 주의사항 머지
                if (result.suggestedCaution() != null && !result.suggestedCaution().isBlank()) {
                    mergePersonaCaution(correction.getPersonaId(), result.suggestedCaution(), correction.getId());
                    correction.setPersonaCaution(result.suggestedCaution());
                }

                // 전역 금지 규칙 저장
                for (String rule : result.suggestedGlobalRules()) {
                    if (rule == null || rule.isBlank()) continue;
                    globalRuleRepository.save(AiGlobalRule.builder()
                            .ruleText(rule.trim()).scope("ALL")
                            .sourceCorrectionId(correction.getId())
                            .active(true).createdBy(adminId).build());
                }

                // example_bank 환류
                try {
                    aiLearningBridge.saveCorrectedAsync(
                            correction.getCorrectedText(),
                            "POST".equals(correction.getTargetType()) ? "POST" : "COMMENT",
                            correction.getCategory(), 1.0);
                    correction.setPushedToBank(true);
                } catch (Exception e) {
                    log.warn("[ai-correction] batch bank push failed correctionId={}: {}", correction.getId(), e.getMessage());
                }

                correction.setStatus("PROCESSED");
                correctionRepository.save(correction);
                processed++;
                log.debug("[ai-correction] batch processed correctionId={}", correction.getId());
            } catch (Exception e) {
                log.warn("[ai-correction] batch failed correctionId={}: {}", correction.getId(), e.getMessage());
            }
        }

        log.info("[ai-correction] batch complete — {}/{} processed", processed, pending.size());
    }

    // =====================================================================
    // 일반 수정(수정 버튼)에서 학습 데이터 캡처
    // =====================================================================

    /**
     * 관리자가 "수정" 버튼으로 본문을 편집할 때 원본→수정본을 PENDING 상태로 저장.
     * 나중에 ai-rules 페이지에서 분석 후 규칙으로 승격 가능.
     * 변경 없으면 저장 스킵.
     */
    @Transactional
    public void captureEdit(String targetType, String targetId,
                            String originalText, String correctedText, String adminId) {
        if (originalText == null || correctedText == null) return;
        if (originalText.equals(correctedText)) return; // 변경 없으면 스킵

        String personaId = fetchPersonaId(targetType, targetId);
        String category  = fetchCategory(targetType, targetId);

        AiContentCorrection correction = AiContentCorrection.builder()
                .targetType(targetType)
                .targetId(targetId)
                .personaId(personaId)
                .category(category)
                .originalText(originalText)
                .correctedText(correctedText)
                .adminId(adminId)
                .status("PENDING")
                .appliedLive(true) // 이미 라이브 반영됨
                .pushedToBank(false)
                .build();
        correctionRepository.save(correction);
        log.debug("[ai-correction] captured edit targetType={} targetId={}", targetType, targetId);
    }

    // =====================================================================
    // PENDING 첨삭 → 규칙 분석 + 적용 (ai-rules 페이지에서 호출)
    // =====================================================================

    public record ApplyRequest(
        long correctionId,
        String scope,           // "PERSONA" | "GLOBAL" | "BOTH"
        String personaCaution,  // nullable
        List<String> globalRules,
        boolean pushToBank
    ) {}

    /**
     * 기존 correction 레코드를 LLM으로 분석, AnalyzeResult 반환.
     * DB 변경 없음.
     */
    public AnalyzeResult analyzeById(long correctionId) throws Exception {
        AiContentCorrection correction = correctionRepository.findById(correctionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "CORRECTION_NOT_FOUND"));

        String diffPrompt = buildDiffPrompt(correction.getOriginalText(), correction.getCorrectedText(),
                correction.getAdminOpinion());
        String llmResponse = llmProvider.invoke(diffPrompt, correctionModel);
        return parseAnalyzeResponse(llmResponse, correction.getPersonaId(), correction.getOriginalText());
    }

    /**
     * PENDING 첨삭 레코드에 규칙을 적용하고 PROCESSED로 승격.
     * scope: "PERSONA" → voice_profile 갱신만, "GLOBAL" → 전역 규칙만, "BOTH" → 둘 다
     */
    @Transactional
    public CommitResult applyById(ApplyRequest req, String adminId) {
        AiContentCorrection correction = correctionRepository.findById(req.correctionId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "CORRECTION_NOT_FOUND"));

        String personaId = correction.getPersonaId();
        long correctionId = req.correctionId();
        boolean cautionApplied = false;
        int rulesCreated = 0;

        // 페르소나 주의사항
        boolean applyPersona = "PERSONA".equals(req.scope()) || "BOTH".equals(req.scope());
        if (applyPersona && req.personaCaution() != null && !req.personaCaution().isBlank()) {
            mergePersonaCaution(personaId, req.personaCaution().trim(), correctionId);
            correction.setPersonaCaution(req.personaCaution().trim());
            cautionApplied = true;
        }

        // 전역 규칙
        boolean applyGlobal = "GLOBAL".equals(req.scope()) || "BOTH".equals(req.scope());
        if (applyGlobal && req.globalRules() != null) {
            for (String rule : req.globalRules()) {
                if (rule == null || rule.isBlank()) continue;
                globalRuleRepository.save(AiGlobalRule.builder()
                        .ruleText(rule.trim())
                        .scope("ALL")
                        .sourceCorrectionId(correctionId)
                        .active(true)
                        .createdBy(adminId)
                        .build());
                rulesCreated++;
            }
        }

        // example_bank 환류
        if (req.pushToBank()) {
            try {
                aiLearningBridge.saveCorrectedAsync(
                        correction.getCorrectedText(),
                        "POST".equals(correction.getTargetType()) ? "POST" : "COMMENT",
                        correction.getCategory(), 1.0);
                correction.setPushedToBank(true);
            } catch (Exception e) {
                log.warn("[ai-correction] example_bank 환류 실패: {}", e.getMessage());
            }
        }

        correction.setStatus("PROCESSED");
        correctionRepository.save(correction);

        log.info("[ai-correction] applyById correctionId={} scope={} caution={} rules={}",
                correctionId, req.scope(), cautionApplied, rulesCreated);

        return new CommitResult(correctionId, false, rulesCreated, cautionApplied);
    }

    /**
     * PENDING 첨삭을 SKIPPED로 표시 (학습 데이터로 사용 안 함).
     */
    @Transactional
    public void skipById(long correctionId) {
        AiContentCorrection correction = correctionRepository.findById(correctionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "CORRECTION_NOT_FOUND"));
        correction.setStatus("SKIPPED");
        correctionRepository.save(correction);
    }
}
