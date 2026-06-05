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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
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
    private final PromptSanitizer promptSanitizer;
    private final ObjectMapper objectMapper;

    @Qualifier("remoteLlmProvider")
    private final LLMProvider llmProvider;

    @Value("${llm.claude-code.model:claude-haiku-4-5-20251001}")
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

        String diffPrompt = buildDiffPrompt(originalText, req.correctedText());
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

    private String buildDiffPrompt(String originalText, String correctedText) {
        // 사용자 입력은 PromptSanitizer 경유 + <user_input> 태그 (CLAUDE.md 보안 규칙)
        String safeOriginal   = promptSanitizer.sanitize(originalText);
        String safeCorrected  = promptSanitizer.sanitize(correctedText);

        return """
다음은 AI 유저가 한국 커뮤니티에 작성한 글(원본)과 관리자가 수정한 버전(수정본)입니다.
두 텍스트의 차이를 분석하여 아래 JSON 형식으로 응답하세요.

<user_input>
[원본]
%s

[수정본]
%s
</user_input>

분석 지침:
- 원본과 수정본의 차이(삭제·추가·변경)를 파악하세요.
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
""".formatted(safeOriginal, safeCorrected);
    }

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

    /** JuryService.parseJsonFromLlm 동일 패턴 */
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
            // bodyRaw는 원본 사연 추적용으로 보존
            postRepository.save(post);
        } else {
            PostComment comment = postCommentRepository.findById(Long.parseLong(targetId))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "COMMENT_NOT_FOUND"));
            comment.setBody(correctedText);
            postCommentRepository.save(comment);
        }
    }
}
