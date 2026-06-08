package com.againspring.service.ai;

import com.againspring.domain.ai.AiContentCorrection;
import com.againspring.llm.LLMProvider;
import com.againspring.llm.PromptSanitizer;
import com.againspring.repository.ai.AiContentCorrectionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * AI 첨삭 일괄 분석 — map-reduce 오케스트레이션 서비스.
 *
 * 흐름:
 * 1. MAP  — PENDING 첨삭을 22,000자 청크로 분할, Sonnet으로 청크별 패턴 추출.
 * 2. REDUCE — 모든 observation을 Opus로 통합, 중복 제거 + scope 판정(GLOBAL/PERSONA).
 * 3. 통합 플랜을 in-memory job에 저장 (자동 적용 안 함).
 * 4. 관리자가 검토 후 apply-batch-plan을 호출해 AiCorrectionService.applyConsolidatedPlan 실행.
 *
 * CLI 전용: remoteLlmProvider만 사용 (CLAUDE.md 절대 규칙 — API 호출 금지).
 * TTL: job은 약 30분 후 자동 정리.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiBatchLearningService {

    private final AiContentCorrectionRepository correctionRepository;
    private final AiCorrectionService aiCorrectionService;
    private final PromptSanitizer promptSanitizer;
    private final ObjectMapper objectMapper;

    @Qualifier("remoteLlmProvider")
    private final LLMProvider llmProvider;

    /** 자기 주입 — @Async 자기 호출 시 AOP 프록시 우회 방지 */
    @Lazy
    @Autowired
    private AiBatchLearningService self;

    /** MAP 단계: 청크별 패턴 추출 — Sonnet */
    @Value("${llm.correction.model:claude-sonnet-4-6}")
    private String mapModel;

    /** REDUCE 단계: 통합 + scope 판정 — Opus (1회, Sonnet 폴백) */
    @Value("${llm.correction.reduce-model:claude-opus-4-8}")
    private String reduceModel;

    // ── 청킹 파라미터 ──────────────────────────────────────────────────────────
    private static final int MAX_CHARS_PER_CHUNK  = 22_000; // ≈ 11K 토큰
    private static final int MAX_ITEMS_PER_CHUNK  = 40;
    private static final int REDUCE_TIER2_THRESHOLD = 150;  // observation 수 초과 시 계층 reduce

    // ── in-memory job 레지스트리 ───────────────────────────────────────────────
    private static final long JOB_TTL_MS = 30 * 60 * 1_000L; // 30분

    private final ConcurrentHashMap<String, BatchJob> jobRegistry = new ConcurrentHashMap<>();

    // =====================================================================
    // 공개 타입
    // =====================================================================

    public enum JobStatus { RUNNING, READY, FAILED }

    /** MAP 단계에서 추출된 단일 관찰 */
    public record Observation(
            List<Long>   corrIds,
            List<String> personaIds,
            String       kind,     // STYLE|TONE|CONTENT|STRUCTURE
            String       pattern,
            String       suggestedRule,
            String       scopeHint  // GLOBAL|PERSONA
    ) {}

    /** REDUCE 결과 — 전역 규칙 항목 */
    public record GlobalRuleProposal(
            String       ruleText,
            String       scope,          // ALL|POST|COMMENT
            List<Long>   sourceCorrIds,
            String       rationale
    ) {}

    /** REDUCE 결과 — 페르소나 주의사항 항목 */
    public record PersonaCautionProposal(
            String       personaId,
            String       cautionText,
            List<Long>   sourceCorrIds,
            String       rationale
    ) {}

    /** 관리자가 검토할 통합 플랜 */
    public record BatchPlan(
            List<GlobalRuleProposal>    globalRules,
            List<PersonaCautionProposal> personaCautions,
            List<Long>                  allSourceCorrIds
    ) {}

    /** 관리자가 승인한 적용 요청 */
    public record ApplyBatchRequest(
            List<ApprovedGlobalRule>   globalRules,
            List<ApprovedPersonaCaution> personaCautions,
            boolean                    pushToBank
    ) {}

    public record ApprovedGlobalRule(
            String ruleText,
            String scope,
            List<Long> sourceCorrIds
    ) {}

    public record ApprovedPersonaCaution(
            String personaId,
            String cautionText,
            List<Long> sourceCorrIds
    ) {}

    /** job 상태 스냅샷 (컨트롤러 응답용) */
    public record JobSnapshot(
            String     jobId,
            JobStatus  status,
            int        pendingCount,
            int        chunksDone,
            int        chunksTotal,
            BatchPlan  plan,
            String     error
    ) {}

    // ── 내부 가변 Job ──────────────────────────────────────────────────────────
    private static class BatchJob {
        final String    jobId;
        final String    adminId;
        final int       pendingCount;
        final int       chunksTotal;
        volatile JobStatus status = JobStatus.RUNNING;
        final AtomicInteger chunksDone = new AtomicInteger(0);
        volatile BatchPlan  plan;
        volatile String     error;
        final Instant createdAt = Instant.now();

        BatchJob(String jobId, String adminId, int pendingCount, int chunksTotal) {
            this.jobId        = jobId;
            this.adminId      = adminId;
            this.pendingCount = pendingCount;
            this.chunksTotal  = chunksTotal;
        }

        JobSnapshot snapshot() {
            return new JobSnapshot(jobId, status, pendingCount,
                    chunksDone.get(), chunksTotal, plan, error);
        }
    }

    // =====================================================================
    // 공개 API
    // =====================================================================

    /**
     * PENDING 첨삭 일괄 분석을 시작한다.
     * 즉시 jobId를 반환하고 비동기로 MAP → REDUCE → plan 저장.
     */
    public String startAnalysis(String adminId) {
        List<AiContentCorrection> pending = correctionRepository.findByStatus("PENDING");
        if (pending.isEmpty()) {
            throw new IllegalStateException("분석 대기 중인 첨삭이 없습니다.");
        }

        List<List<AiContentCorrection>> chunks = buildChunks(pending);
        String jobId = UUID.randomUUID().toString();
        BatchJob job = new BatchJob(jobId, adminId, pending.size(), chunks.size());
        jobRegistry.put(jobId, job);

        self.runAnalysisAsync(job, chunks);

        log.info("[batch-learning] startAnalysis jobId={} pending={} chunks={}",
                jobId, pending.size(), chunks.size());
        return jobId;
    }

    /** job 상태 조회 */
    public Optional<JobSnapshot> getJob(String jobId) {
        BatchJob job = jobRegistry.get(jobId);
        return Optional.ofNullable(job == null ? null : job.snapshot());
    }

    /**
     * 관리자 승인된 플랜을 적용한다.
     * LLM 없음 — AiCorrectionService.applyConsolidatedPlan에 위임.
     */
    public AiCorrectionService.ConsolidatedApplyResult applyPlan(ApplyBatchRequest req, String adminId) {
        List<AiCorrectionService.GlobalRuleItem> rules = req.globalRules().stream()
                .map(r -> new AiCorrectionService.GlobalRuleItem(r.ruleText(), r.scope(), r.sourceCorrIds()))
                .collect(Collectors.toList());

        List<AiCorrectionService.PersonaCautionItem> cautions = req.personaCautions().stream()
                .map(c -> new AiCorrectionService.PersonaCautionItem(c.personaId(), c.cautionText(), c.sourceCorrIds()))
                .collect(Collectors.toList());

        // 전체 sourceCorrIds 수집
        Set<Long> allIds = new LinkedHashSet<>();
        req.globalRules().forEach(r -> { if (r.sourceCorrIds() != null) allIds.addAll(r.sourceCorrIds()); });
        req.personaCautions().forEach(c -> { if (c.sourceCorrIds() != null) allIds.addAll(c.sourceCorrIds()); });

        return aiCorrectionService.applyConsolidatedPlan(rules, cautions, new ArrayList<>(allIds),
                req.pushToBank(), adminId);
    }

    // =====================================================================
    // 비동기 MAP → REDUCE 실행
    // =====================================================================

    @Async("taskExecutor")
    public void runAnalysisAsync(BatchJob job, List<List<AiContentCorrection>> chunks) {
        try {
            // ── MAP 단계 ──────────────────────────────────────────────────
            List<Observation> allObservations = new ArrayList<>();
            Set<Long> allCorrIds = new LinkedHashSet<>();

            for (List<AiContentCorrection> chunk : chunks) {
                try {
                    String chunkPrompt = buildMapPrompt(chunk);
                    String response    = llmProvider.invoke(chunkPrompt, mapModel);
                    List<Observation> obs = parseObservations(response);
                    allObservations.addAll(obs);

                    // source corr_ids 수집
                    for (AiContentCorrection c : chunk) allCorrIds.add(c.getId());
                    job.chunksDone.incrementAndGet();

                    log.debug("[batch-learning] MAP chunk {}/{} done — {} obs",
                            job.chunksDone.get(), job.chunksTotal, obs.size());
                } catch (Exception e) {
                    log.warn("[batch-learning] MAP chunk failed: {}", e.getMessage());
                    // 청크 실패 시 건너뜀 — 부분 결과로 계속
                    job.chunksDone.incrementAndGet();
                }
            }

            if (allObservations.isEmpty()) {
                job.error = "MAP 단계에서 유의미한 패턴을 추출하지 못했습니다.";
                job.status = JobStatus.FAILED;
                return;
            }

            // ── REDUCE 단계 (계층 reduce if >150 obs) ──────────────────────
            BatchPlan plan;
            if (allObservations.size() > REDUCE_TIER2_THRESHOLD) {
                // 계층 reduce: 그룹 사전요약(Sonnet) → 최종 통합(Opus)
                plan = tieredReduce(allObservations, new ArrayList<>(allCorrIds), job.adminId);
            } else {
                plan = singleReduce(allObservations, new ArrayList<>(allCorrIds), job.adminId);
            }

            job.plan   = plan;
            job.status = JobStatus.READY;
            log.info("[batch-learning] REDUCE done jobId={} global={} persona={}",
                    job.jobId, plan.globalRules().size(), plan.personaCautions().size());

        } catch (Exception e) {
            log.error("[batch-learning] 분석 실패 jobId={}: {}", job.jobId, e.getMessage());
            job.error  = e.getMessage();
            job.status = JobStatus.FAILED;
        }
    }

    // =====================================================================
    // 청킹
    // =====================================================================

    private List<List<AiContentCorrection>> buildChunks(List<AiContentCorrection> items) {
        List<List<AiContentCorrection>> chunks = new ArrayList<>();
        List<AiContentCorrection> current = new ArrayList<>();
        int currentChars = 0;

        for (AiContentCorrection item : items) {
            int itemChars = charLen(item.getOriginalText())
                          + charLen(item.getCorrectedText())
                          + charLen(item.getAdminOpinion());

            boolean overflow = (currentChars + itemChars > MAX_CHARS_PER_CHUNK && !current.isEmpty())
                             || current.size() >= MAX_ITEMS_PER_CHUNK;

            if (overflow) {
                chunks.add(current);
                current = new ArrayList<>();
                currentChars = 0;
            }
            current.add(item);
            currentChars += itemChars;
        }
        if (!current.isEmpty()) chunks.add(current);
        return chunks;
    }

    private int charLen(String s) { return s == null ? 0 : s.length(); }

    // =====================================================================
    // MAP 프롬프트
    // =====================================================================

    private String buildMapPrompt(List<AiContentCorrection> chunk) {
        StringBuilder items = new StringBuilder();
        for (AiContentCorrection c : chunk) {
            String safeOrig = promptSanitizer.sanitize(c.getOriginalText());
            String safeCor  = promptSanitizer.sanitize(c.getCorrectedText());
            String opinion  = (c.getAdminOpinion() != null && !c.getAdminOpinion().isBlank())
                    ? promptSanitizer.sanitize(c.getAdminOpinion()) : "(없음)";
            String category = c.getCategory() != null ? c.getCategory() : "GENERAL";

            items.append(String.format("""
[#%d | persona=%s | %s | %s]
원본: %s
수정본: %s
관리자 의견: %s
---
""", c.getId(), c.getPersonaId(), c.getTargetType(), category,
     safeOrig, safeCor, opinion));
        }

        return """
<conversation_history>
당신은 AI 글쓰기 규칙 분석가입니다. 한국 커뮤니티 AI 유저의 글쓰기를 개선하는 규칙을 추출하는 것이 목표입니다.
</conversation_history>

아래는 관리자가 AI 유저의 글/댓글을 첨삭한 기록들입니다.
각 항목의 원본→수정본 차이와 관리자 의견을 분석해 공통 패턴(observation)을 추출하세요.

<user_input>
%s
</user_input>

분석 규칙:
- 여러 항목에 걸쳐 반복되는 패턴을 하나의 observation으로 묶을 수 있습니다.
- scope_hint: 특정 페르소나에만 해당하면 PERSONA, 모든 AI 유저에게 해당하면 GLOBAL.
- kind: STYLE(문체), TONE(어조), CONTENT(내용선택), STRUCTURE(글 구조) 중 하나.
- suggested_rule: "~하지 말 것" 또는 "~할 것" 형태의 단문 규칙.
- corr_ids에는 관련 항목의 # 번호를 넣으세요.

반드시 JSON만 반환하세요 (마크다운 없이):
{"observations":[{"corr_ids":[숫자,...],"persona_ids":["아이디",...],"kind":"STYLE","pattern":"...(1~2문장)","suggested_rule":"...","scope_hint":"GLOBAL"}]}
""".formatted(items.toString());
    }

    // =====================================================================
    // REDUCE 단계
    // =====================================================================

    private BatchPlan singleReduce(List<Observation> observations, List<Long> allCorrIds, String adminId) {
        String reducePrompt = buildReducePrompt(observations, allCorrIds);

        // Opus 시도 → 실패 시 Sonnet 폴백
        String response;
        try {
            response = llmProvider.invoke(reducePrompt, reduceModel);
            log.debug("[batch-learning] REDUCE: Opus 호출 성공");
        } catch (Exception opusEx) {
            log.warn("[batch-learning] REDUCE: Opus 실패({}), Sonnet으로 폴백", opusEx.getMessage());
            try {
                response = llmProvider.invoke(reducePrompt, mapModel);
            } catch (Exception ex) {
                throw new RuntimeException("REDUCE 단계 LLM 호출 실패: " + ex.getMessage(), ex);
            }
        }

        return parseReduceResponse(response, allCorrIds);
    }

    /** 계층 reduce: observation을 N그룹으로 Sonnet 사전요약 → Opus 최종 통합 */
    private BatchPlan tieredReduce(List<Observation> observations, List<Long> allCorrIds, String adminId) {
        // 그룹 크기 ≈ 75 (2그룹 분할 기준)
        int groupSize = 75;
        List<Observation> summarized = new ArrayList<>();

        for (int i = 0; i < observations.size(); i += groupSize) {
            List<Observation> group = observations.subList(i, Math.min(i + groupSize, observations.size()));
            String groupPrompt = buildReducePrompt(group, allCorrIds);
            try {
                String groupResponse = llmProvider.invoke(groupPrompt, mapModel);
                // 사전요약 결과에서 observations 추출
                List<Observation> groupObs = parseObservations(groupResponse);
                if (!groupObs.isEmpty()) {
                    summarized.addAll(groupObs);
                } else {
                    // 파싱 실패 시 원본 포함
                    summarized.addAll(group);
                }
            } catch (Exception e) {
                log.warn("[batch-learning] tiered reduce group failed: {}", e.getMessage());
                summarized.addAll(group);
            }
        }

        return singleReduce(summarized, allCorrIds, adminId);
    }

    private String buildReducePrompt(List<Observation> observations, List<Long> allCorrIds) {
        StringBuilder obsText = new StringBuilder();
        for (int i = 0; i < observations.size(); i++) {
            Observation o = observations.get(i);
            obsText.append(String.format(
                "[%d] corr_ids=%s personas=%s kind=%s scope_hint=%s\n패턴: %s\n제안 규칙: %s\n\n",
                i + 1, o.corrIds(), o.personaIds(), o.kind(), o.scopeHint(), o.pattern(), o.suggestedRule()));
        }

        long personaCount = observations.stream()
                .flatMap(o -> o.personaIds().stream()).distinct().count();

        return """
<conversation_history>
당신은 AI 글쓰기 규칙 통합 전문가입니다. 여러 첨삭 분석 결과를 종합해 중복 없는 학습 규칙 세트를 만드는 것이 목표입니다.
</conversation_history>

아래는 AI 유저 첨삭 분석에서 추출된 observation 목록입니다.
총 %d개의 observation, 관련 페르소나 약 %d명.

<user_input>
%s
</user_input>

통합 작업:
1. 동일/유사 의미의 observation을 병합해 중복을 제거하세요.
2. 각 규칙의 최종 scope를 판정하세요:
   - GLOBAL: 2명 이상의 페르소나에서 반복되거나, 보편적인 한국 커뮤니티 글쓰기 원칙인 경우
   - PERSONA: 특정 1명의 페르소나 말투·주제·개성에만 국한되는 경우
3. global_rules: scope=GLOBAL인 규칙들. scope는 ALL/POST/COMMENT 중 하나.
4. persona_cautions: scope=PERSONA인 규칙들 — 해당 페르소나 ID별로 1개의 핵심 주의사항.
5. source_corr_ids: 이 규칙을 뒷받침하는 원래 observation의 corr_ids를 합쳐서 넣으세요.
6. rationale: 이 분류 근거를 1문장으로.

반드시 JSON만 반환하세요 (마크다운 없이):
{"global_rules":[{"rule_text":"...","scope":"ALL","source_corr_ids":[숫자,...],"rationale":"..."}],"persona_cautions":[{"persona_id":"...","caution_text":"...","source_corr_ids":[숫자,...],"rationale":"..."}]}
""".formatted(observations.size(), personaCount, obsText.toString());
    }

    // =====================================================================
    // 파싱 헬퍼
    // =====================================================================

    private List<Observation> parseObservations(String response) {
        try {
            JsonNode root = parseJsonFromLlm(response);

            // REDUCE 응답(global_rules + persona_cautions)인 경우 — 역 변환
            if (root.has("global_rules") || root.has("persona_cautions")) {
                return convertReduceToObservations(root);
            }

            // MAP 응답(observations 배열)
            JsonNode obsNode = root.path("observations");
            if (!obsNode.isArray()) return List.of();

            List<Observation> result = new ArrayList<>();
            for (JsonNode item : obsNode) {
                List<Long> corrIds = new ArrayList<>();
                item.path("corr_ids").forEach(n -> corrIds.add(n.asLong()));

                List<String> personaIds = new ArrayList<>();
                item.path("persona_ids").forEach(n -> personaIds.add(n.asText()));

                result.add(new Observation(
                        corrIds, personaIds,
                        item.path("kind").asText("STYLE"),
                        item.path("pattern").asText(""),
                        item.path("suggested_rule").asText(""),
                        item.path("scope_hint").asText("GLOBAL")
                ));
            }
            return result;
        } catch (Exception e) {
            log.warn("[batch-learning] parseObservations 실패: {}", e.getMessage());
            return List.of();
        }
    }

    private List<Observation> convertReduceToObservations(JsonNode root) {
        List<Observation> result = new ArrayList<>();
        root.path("global_rules").forEach(r -> result.add(new Observation(
                corrIdsFrom(r), List.of(),
                "CONTENT", r.path("rule_text").asText(""), r.path("rule_text").asText(""), "GLOBAL")));
        root.path("persona_cautions").forEach(c -> result.add(new Observation(
                corrIdsFrom(c), List.of(c.path("persona_id").asText()),
                "STYLE", c.path("caution_text").asText(""), c.path("caution_text").asText(""), "PERSONA")));
        return result;
    }

    private List<Long> corrIdsFrom(JsonNode node) {
        List<Long> ids = new ArrayList<>();
        node.path("source_corr_ids").forEach(n -> ids.add(n.asLong()));
        return ids;
    }

    private BatchPlan parseReduceResponse(String response, List<Long> allCorrIds) {
        try {
            JsonNode root = parseJsonFromLlm(response);

            List<GlobalRuleProposal> globalRules = new ArrayList<>();
            root.path("global_rules").forEach(r -> {
                String ruleText = r.path("rule_text").asText("").trim();
                if (ruleText.isBlank() || AiCorrectionService.isErrorSignature(ruleText)) return;
                List<Long> srcIds = new ArrayList<>();
                r.path("source_corr_ids").forEach(n -> srcIds.add(n.asLong()));
                globalRules.add(new GlobalRuleProposal(
                        ruleText,
                        r.path("scope").asText("ALL"),
                        srcIds,
                        r.path("rationale").asText("")
                ));
            });

            List<PersonaCautionProposal> personaCautions = new ArrayList<>();
            root.path("persona_cautions").forEach(c -> {
                String cautionText = c.path("caution_text").asText("").trim();
                if (cautionText.isBlank() || AiCorrectionService.isErrorSignature(cautionText)) return;
                List<Long> srcIds = new ArrayList<>();
                c.path("source_corr_ids").forEach(n -> srcIds.add(n.asLong()));
                personaCautions.add(new PersonaCautionProposal(
                        c.path("persona_id").asText(""),
                        cautionText,
                        srcIds,
                        c.path("rationale").asText("")
                ));
            });

            return new BatchPlan(globalRules, personaCautions, allCorrIds);

        } catch (Exception e) {
            log.error("[batch-learning] parseReduceResponse 실패: {}", e.getMessage());
            return new BatchPlan(List.of(), List.of(), allCorrIds);
        }
    }

    /** JuryService 동일 패턴 — JSON 추출 */
    private JsonNode parseJsonFromLlm(String response) throws Exception {
        String json = response;
        if (json.contains("```json")) {
            int s = json.indexOf("```json") + 7, e = json.lastIndexOf("```");
            if (e > s) return objectMapper.readTree(json.substring(s, e).trim());
        }
        if (json.contains("```")) {
            int s = json.indexOf("```") + 3, e = json.lastIndexOf("```");
            if (e > s) {
                String candidate = json.substring(s, e).trim();
                if (candidate.startsWith("{")) return objectMapper.readTree(candidate);
            }
        }
        int bs = json.indexOf('{'), be = json.lastIndexOf('}');
        if (bs >= 0 && be > bs) return objectMapper.readTree(json.substring(bs, be + 1));
        return objectMapper.readTree(json.trim());
    }

    // =====================================================================
    // TTL 청소 (30분마다)
    // =====================================================================

    @Scheduled(fixedDelay = 30 * 60 * 1_000L)
    void cleanupExpiredJobs() {
        Instant cutoff = Instant.now().minusMillis(JOB_TTL_MS);
        int removed = 0;
        for (Iterator<Map.Entry<String, BatchJob>> it = jobRegistry.entrySet().iterator(); it.hasNext(); ) {
            BatchJob job = it.next().getValue();
            if (job.createdAt.isBefore(cutoff)) {
                it.remove();
                removed++;
            }
        }
        if (removed > 0) {
            log.debug("[batch-learning] TTL cleanup: {} jobs removed", removed);
        }
    }
}
