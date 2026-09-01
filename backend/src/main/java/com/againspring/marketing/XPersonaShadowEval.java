package com.againspring.marketing;

import com.againspring.domain.marketing.XOpsAction;
import com.againspring.domain.marketing.XPersonaEval;
import com.againspring.domain.marketing.XPersonaExample;
import com.againspring.llm.LLMProvider;
import com.againspring.llm.PromptSanitizer;
import com.againspring.llm.prompt.PromptLoader;
import com.againspring.repository.marketing.XOpsActionRepository;
import com.againspring.repository.marketing.XPersonaEvalRepository;
import com.againspring.repository.marketing.XPersonaExampleRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Held-out reproduction + 말투 채점. Parent should call from
 * {@code XPersonaLearnService.runNow} and Admin GET metrics.
 *
        <p>Held-out: {@code composeOutbound(postText, List.of(), null, tweetId)}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class XPersonaShadowEval {

    static final String JUDGE_PROMPT = "marketing/x-persona-judge.md";
    static final int CAP_PER_RUN = 10;
    static final Duration WINDOW = Duration.ofDays(28);
    static final double GATE_AVG = 95.0;
    static final double GATE_DELETE_RATE = 0.02;
    static final long GATE_MIN_N = 30;

    public record MimicryMetrics(
        double avg28d,
        long sampleCount,
        Double deleteRate28d,
        boolean gatePassed,
        boolean sampleInsufficient
    ) {}

    public record JudgeScores(
        int overall,
        int tone,
        int length,
        int texture,
        int content,
        String note
    ) {}

    private final XCommentComposer commentComposer;
    private final LLMProvider llmProvider;
    private final PromptLoader promptLoader;
    private final PromptSanitizer promptSanitizer;
    private final ObjectMapper objectMapper;
    private final XPersonaEvalRepository evalRepository;
    private final XPersonaExampleRepository exampleRepository;
    private final XOpsActionRepository xOpsActionRepository;

    @Value("${llm.enabled:true}")
    private boolean llmEnabled;

    @Value("${marketing.x.persona-learn-model:claude-sonnet-5}")
    private String model;

    /**
     * Score newly saved TIMELINE gold (postText present, no photo), cap 10.
     * Call from {@code XPersonaLearnService.runNow} after persist, when llm is on.
     */
    public void runForNewGold(List<XPersonaExample> newlySaved) {
        if (!llmEnabled || newlySaved == null || newlySaved.isEmpty()) {
            return;
        }
        int scored = 0;
        for (XPersonaExample ex : newlySaved) {
            if (scored >= CAP_PER_RUN) {
                break;
            }
            if (!eligible(ex)) {
                continue;
            }
            if (evalRepository.existsByExampleId(ex.getId())) {
                continue;
            }
            if (evaluateOne(ex)) {
                scored++;
            }
        }
    }

    /**
     * Fallback when the learn service does not pass the new-gold list:
     * recent TIMELINE rows, same filters, cap 10.
     */
    public void runAfterLearn(Instant now) {
        if (!llmEnabled) {
            return;
        }
        List<XPersonaExample> recent = exampleRepository.findTop40BySourceOrderByCreatedAtDesc(
            XPersonaExample.Source.TIMELINE);
        runForNewGold(recent);
    }

    /** Admin GET / learn status: 28d overall average + DELETED_AUTO / POSTED(in+out). */
    public MimicryMetrics metrics() {
        return metrics(Instant.now());
    }

    MimicryMetrics metrics(Instant now) {
        Instant start = now.minus(WINDOW);
        List<XPersonaEval> rows = evalRepository.findByCreatedAtGreaterThanEqual(start);
        long n = 0;
        long sum = 0;
        for (XPersonaEval row : rows) {
            if (row.getCreatedAt() != null && row.getCreatedAt().isAfter(now)) {
                continue;
            }
            Integer overall = row.getScoreOverall();
            if (overall == null) {
                continue;
            }
            sum += overall;
            n++;
        }
        double avg = n == 0 ? 0.0 : (sum / (double) n);
        boolean insufficient = n < GATE_MIN_N;

        long deleted = exampleRepository.countBySourceAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            XPersonaExample.Source.DELETED_AUTO, start, now);
        long postedOut = xOpsActionRepository.countByKindAndStatusAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            XOpsAction.Kind.OUTBOUND, XOpsAction.Status.POSTED, start, now);
        long postedIn = xOpsActionRepository.countByKindAndStatusAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            XOpsAction.Kind.INBOUND, XOpsAction.Status.POSTED, start, now);
        long denom = postedOut + postedIn;
        // denom 0: rate unknown — null so gate cannot pass on an empty POSTED window
        Double deleteRate = denom == 0 ? null : (deleted / (double) denom);

        boolean gate = !insufficient
            && avg >= GATE_AVG
            && deleteRate != null
            && deleteRate <= GATE_DELETE_RATE;
        return new MimicryMetrics(avg, n, deleteRate, gate, insufficient);
    }

    private boolean eligible(XPersonaExample ex) {
        if (ex == null || ex.getId() == null) {
            return false;
        }
        if (ex.getSource() != XPersonaExample.Source.TIMELINE) {
            return false;
        }
        if (ex.isHasPhoto()) {
            return false;
        }
        String post = ex.getPostText();
        return post != null && !post.isBlank();
    }

    /**
     * @return true if this example consumed a cap slot (compose attempted).
     */
    private boolean evaluateOne(XPersonaExample ex) {
        XCommentComposer.Draft draft;
        try {
            draft = commentComposer.composeOutbound(
                ex.getPostText(), List.of(), null, ex.getTweetId());
        } catch (Exception e) {
            log.warn("[x-persona-eval] compose failed exampleId={}: {}", ex.getId(), e.getMessage());
            return true;
        }
        if (draft == null || draft.skip() || draft.body() == null || draft.body().isBlank()) {
            return true;
        }
        JudgeScores scores = judge(ex, draft.body());
        if (scores == null) {
            return true;
        }
        evalRepository.save(XPersonaEval.builder()
            .exampleId(ex.getId())
            .tweetId(ex.getTweetId())
            .botBody(draft.body())
            .scoreOverall(scores.overall())
            .scoreTone(scores.tone())
            .scoreLength(scores.length())
            .scoreTexture(scores.texture())
            .scoreContent(scores.content())
            .judgeNote(scores.note())
            .createdAt(Instant.now())
            .build());
        return true;
    }

    private JudgeScores judge(XPersonaExample ex, String botBody) {
        String instructions;
        try {
            instructions = promptLoader.get(JUDGE_PROMPT);
        } catch (Exception e) {
            log.warn("[x-persona-eval] judge prompt missing: {}", e.getMessage());
            return null;
        }
        String safeSit = promptSanitizer.sanitize(ex.getPostText());
        String safeOp = promptSanitizer.sanitize(ex.getOperatorBody());
        String safeBot = promptSanitizer.sanitize(botBody);
        String prompt = instructions + """

            상황:
            <user_input>
            %s
            </user_input>

            운영자 본문:
            <user_input>
            %s
            </user_input>

            봇 본문:
            <user_input>
            %s
            </user_input>
            """.formatted(safeSit, safeOp, safeBot);
        String raw;
        try {
            raw = llmProvider.invoke(prompt, model);
        } catch (Exception e) {
            log.warn("[x-persona-eval] judge invoke failed exampleId={}: {}", ex.getId(), e.getMessage());
            return null;
        }
        return parseJudgeJson(objectMapper, raw);
    }

    static JudgeScores parseJudgeJson(ObjectMapper objectMapper, String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String json = XCommentComposer.extractJsonObject(raw);
        try {
            JsonNode n = objectMapper.readTree(json);
            if (n == null || !n.isObject()) {
                return null;
            }
            Integer overall = readScore(n, "overall");
            Integer tone = readScore(n, "tone");
            Integer length = readScore(n, "length");
            Integer texture = readScore(n, "texture");
            Integer content = readScore(n, "content");
            if (overall == null || tone == null || length == null || texture == null || content == null) {
                return null;
            }
            String note = n.has("note") && !n.get("note").isNull() ? n.get("note").asText("") : "";
            return new JudgeScores(overall, tone, length, texture, content, note);
        } catch (Exception e) {
            return null;
        }
    }

    static Integer readScore(JsonNode n, String field) {
        if (n == null || !n.has(field) || n.get(field).isNull()) {
            return null;
        }
        JsonNode v = n.get(field);
        if (!v.isNumber() && !v.isTextual()) {
            return null;
        }
        int i;
        try {
            i = v.isNumber() ? (int) Math.round(v.asDouble()) : Integer.parseInt(v.asText().trim());
        } catch (NumberFormatException e) {
            return null;
        }
        if (i < 0) {
            return 0;
        }
        if (i > 100) {
            return 100;
        }
        return i;
    }
}
