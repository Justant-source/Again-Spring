package com.againspring.marketing;

import com.againspring.domain.ai.SystemSetting;
import com.againspring.domain.marketing.XPersonaExample;
import com.againspring.llm.LLMProvider;
import com.againspring.llm.PromptSanitizer;
import com.againspring.repository.ai.SystemSettingRepository;
import com.againspring.repository.marketing.XPersonaExampleRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Dawn job: pull {@code @againspring_net} timeline, keep operator-typed replies,
 * append them to the voice corpus, and (prod LLM only) refresh the distilled profile.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class XPersonaLearnService {

    public static final String KEY_PROFILE = "marketing.x.persona_profile_json";
    public static final String KEY_INGESTED = "marketing.x.persona_ingested_ids";
    public static final String KEY_LAST_LEARNED = "marketing.x.persona_last_learned_at";
    public static final String KEY_LAST_STATUS = "marketing.x.persona_last_status";
    public static final String KEY_LAST_NEW = "marketing.x.persona_last_new_count";
    public static final String HANDLE = "againspring_net";
    public static final ZoneId KST = ZoneId.of("Asia/Seoul");

    static final String SEED_PROFILE = """
        {"summary":"한 줄로 끊는 구어체. 반말과 해요체 혼용. ㅋㅋㅋ 자주. 귀여움은 명사형 종결. 사연에서는 판결 안 함.","traits":["한 줄","ㅋㅋㅋ","너무귀여움","힘빠지긴 할듯","벌써자?"],"examples":["꺼드럭은 더늘크크가 썼던 말인데 ㅋㅋㅋ","너무귀여움 ㅋㅋㅋㅋ","퇴근하고와서 힘빠지긴 할듯","벌써자?","바이럴같은데 웃참 실패했다 ㅋㅋㅋㅋㅋ"],"avoid":["습니다체","판결","유무죄","누가 잘못"]}
        """.trim();

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");
    private static final int MAX_INGESTED = 400;
    private static final int MAX_NEW_PER_RUN = 40;
    private static final int MAX_EXAMPLE_KEEP = 40;

    private final SystemSettingRepository systemSettingRepository;
    private final MarketingXOpsSettingsService xOpsSettingsService;
    private final FxTwitterXTimelineClient timelineClient;
    private final LLMProvider llmProvider;
    private final PromptSanitizer promptSanitizer;
    private final ObjectMapper objectMapper;
    private final XPersonaExampleRepository exampleRepository;

    @Value("${llm.enabled:true}")
    private boolean llmEnabled;

    @Value("${llm.claude-code.model:claude-haiku-4-5-20251001}")
    private String model;

    public record LearnResult(String status, int newManuals, Instant learnedAt, String summary) {}

    @Transactional(readOnly = true)
    public LearnResult status() {
        String summary = profileSummary(readJson(KEY_PROFILE, SEED_PROFILE));
        Instant at = readInstant(KEY_LAST_LEARNED);
        int neu = readInt(KEY_LAST_NEW, 0);
        String st = readRaw(KEY_LAST_STATUS, "NEVER");
        return new LearnResult(st, neu, at, summary);
    }

    @Transactional
    public LearnResult runIfDue(Instant now) {
        var settings = xOpsSettingsService.get();
        if (!settings.personaLearningEnabled()) {
            return null;
        }
        LocalTime target = LocalTime.parse(settings.personaLearnAt(), HH_MM);
        LocalTime current = now.atZone(KST).toLocalTime().withSecond(0).withNano(0);
        if (!current.equals(target)) {
            return null;
        }
        Instant last = readInstant(KEY_LAST_LEARNED);
        if (last != null) {
            LocalDate lastDay = last.atZone(KST).toLocalDate();
            if (lastDay.equals(now.atZone(KST).toLocalDate())) {
                return null;
            }
        }
        return runNow("scheduler");
    }

    @Transactional
    public LearnResult runNow(String updatedBy) {
        List<XManualStatusClassifier.Status> fetched;
        try {
            fetched = timelineClient.fetchRecent(HANDLE, 6);
        } catch (Exception e) {
            log.warn("[x-persona] fetch failed: {}", e.getMessage());
            return persistMeta("FETCH_FAILED", 0, Instant.now(), updatedBy);
        }

        Set<String> ingested = readIdSet();
        List<XManualStatusClassifier.Status> fresh = new ArrayList<>();
        for (XManualStatusClassifier.Status s : fetched) {
            if (!XManualStatusClassifier.isManual(s, HANDLE)) {
                continue;
            }
            if (ingested.contains(s.id())) {
                continue;
            }
            fresh.add(s);
            if (fresh.size() >= MAX_NEW_PER_RUN) {
                break;
            }
        }

        if (fresh.isEmpty()) {
            return persistMeta("NO_NEW", 0, Instant.now(), updatedBy);
        }

        for (XManualStatusClassifier.Status s : fresh) {
            persistTimelineExample(s);
            ingested.add(s.id());
        }
        saveIngested(ingested, updatedBy);

        ObjectNode profile = readJson(KEY_PROFILE, SEED_PROFILE);
        appendExampleLines(profile, formatCorpusLines());
        String status = refreshProfile(profile, updatedBy);
        return persistMeta(status, fresh.size(), Instant.now(), updatedBy);
    }

    /**
     * After a Telegram drill is stored, fold the corpus into the distilled profile.
     */
    @Transactional
    public String ingestDrillIntoProfile(String updatedBy) {
        ObjectNode profile = readJson(KEY_PROFILE, SEED_PROFILE);
        appendExampleLines(profile, formatCorpusLines());
        return refreshProfile(profile, updatedBy == null ? "telegram" : updatedBy);
    }

    private String refreshProfile(ObjectNode profile, String updatedBy) {
        String status = "INGESTED";
        if (llmEnabled) {
            ObjectNode distilled = distill(profile);
            if (distilled != null) {
                profile = distilled;
                status = "OK";
            } else {
                status = "INGESTED_LLM_SKIP";
            }
        } else {
            status = "INGESTED_LLM_DISABLED";
        }
        saveSetting(KEY_PROFILE, profile.toString(), Instant.now(), updatedBy);
        return status;
    }

    private void persistTimelineExample(XManualStatusClassifier.Status s) {
        if (s == null || s.id() == null || s.id().isBlank()) {
            return;
        }
        if (exampleRepository.existsByTweetId(s.id())) {
            return;
        }
        String body = s.text() != null ? s.text().replace('\n', ' ').trim() : "";
        if (body.isBlank()) {
            return;
        }
        exampleRepository.save(XPersonaExample.builder()
            .source(XPersonaExample.Source.TIMELINE)
            .tweetId(s.id())
            .postText(null)
            .hasPhoto(false)
            .operatorBody(body)
            .createdAt(Instant.now())
            .build());
    }

    String formatCorpusLines() {
        StringBuilder sb = new StringBuilder();
        for (XPersonaExample ex : exampleRepository.findTop20BySourceOrderByCreatedAtDesc(
            XPersonaExample.Source.DRILL)) {
            sb.append(formatPair(ex, 2)).append('\n');
        }
        for (XPersonaExample ex : exampleRepository.findTop20BySourceOrderByCreatedAtDesc(
            XPersonaExample.Source.TIMELINE)) {
            sb.append(formatPair(ex, 1)).append('\n');
        }
        return sb.toString();
    }

    static String formatPair(XPersonaExample ex, int weight) {
        String sit = ex.getPostText() == null || ex.getPostText().isBlank()
            ? "(상황 없음)"
            : ex.getPostText().replace('\n', ' ').trim();
        String body = ex.getOperatorBody() == null ? "" : ex.getOperatorBody().replace('\n', ' ').trim();
        return "가중 " + weight + " / 상황: " + sit + " / 운영자: " + body;
    }

    private ObjectNode distill(ObjectNode current) {
        String corpus = formatCorpusLines();
        if (corpus.isBlank()) {
            return null;
        }
        String safeComments = promptSanitizer.sanitize(corpus);
        String safeProfile = promptSanitizer.sanitize(current.toString());
        String prompt = """
            당신은 X 계정 문체 분석기입니다. 운영자가 직접 단 댓글(상황-댓글 쌍)만 보고 목소리 프로필 JSON을 갱신하세요.
            가중 2(드릴: 상황이 있는 쌍)를 가중 1(타임라인: 댓글만)보다 우선하세요.
            기존 프로필의 결을 유지하되, 새 쌍에서 반복되는 버릇을 보강하세요.
            판결/승패/유무죄 표현을 프로필에 넣지 마세요. 사연 평은 공감만.

            기존 프로필:
            <user_input>
            %s
            </user_input>

            운영자 코퍼스 (상황 / 운영자 댓글):
            <user_input>
            %s
            </user_input>

            JSON only:
            {"summary":"한 문단","traits":["짧은 버릇"],"examples":["실제 댓글 톤 예시"],"avoid":["쓰지 말 것"]}
            """.formatted(safeProfile, safeComments);
        try {
            String raw = llmProvider.invoke(prompt, model);
            if (raw == null || raw.isBlank() || looksLikeLlmError(raw)) {
                return null;
            }
            JsonNode parsed = objectMapper.readTree(extractJsonObject(raw));
            if (!parsed.isObject() || !parsed.path("summary").isTextual()) {
                return null;
            }
            if (looksLikeLlmError(parsed.path("summary").asText())) {
                return null;
            }
            return (ObjectNode) parsed;
        } catch (Exception e) {
            log.warn("[x-persona] distill failed: {}", e.getMessage());
            return null;
        }
    }

    private void appendExampleLines(ObjectNode profile, String corpusLines) {
        ArrayNode examples = profile.withArray("examples");
        LinkedHashSet<String> keep = new LinkedHashSet<>();
        for (JsonNode n : examples) {
            if (n.isTextual() && !n.asText().isBlank()) {
                keep.add(n.asText().trim());
            }
        }
        if (corpusLines != null) {
            for (String line : corpusLines.split("\n")) {
                if (line != null && !line.isBlank()) {
                    keep.add(line.trim());
                }
            }
        }
        examples.removeAll();
        List<String> list = new ArrayList<>(keep);
        int start = Math.max(0, list.size() - MAX_EXAMPLE_KEEP);
        for (int idx = start; idx < list.size(); idx++) {
            examples.add(list.get(idx));
        }
    }

    private LearnResult persistMeta(String status, int neu, Instant at, String updatedBy) {
        saveSetting(KEY_LAST_STATUS, status, at, updatedBy);
        saveSetting(KEY_LAST_NEW, String.valueOf(neu), at, updatedBy);
        saveSetting(KEY_LAST_LEARNED, at.toString(), at, updatedBy);
        return new LearnResult(status, neu, at, profileSummary(readJson(KEY_PROFILE, SEED_PROFILE)));
    }

    private String profileSummary(ObjectNode profile) {
        String s = profile.path("summary").asText("");
        return s.length() > 280 ? s.substring(0, 280) : s;
    }

    private ObjectNode readJson(String key, String fallback) {
        try {
            String raw = readRaw(key, fallback);
            JsonNode n = objectMapper.readTree(raw);
            if (n.isObject()) {
                return (ObjectNode) n;
            }
        } catch (Exception ignored) {
            // fall through to seed
        }
        try {
            return (ObjectNode) objectMapper.readTree(fallback);
        } catch (Exception e) {
            return objectMapper.createObjectNode().put("summary", "");
        }
    }

    private Set<String> readIdSet() {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        try {
            JsonNode n = objectMapper.readTree(readRaw(KEY_INGESTED, "[]"));
            if (n.isArray()) {
                for (JsonNode x : n) {
                    if (x.isTextual()) {
                        ids.add(x.asText());
                    }
                }
            }
        } catch (Exception ignored) {
            // empty
        }
        return ids;
    }

    private void saveIngested(Set<String> ids, String updatedBy) {
        List<String> list = new ArrayList<>(ids);
        int start = Math.max(0, list.size() - MAX_INGESTED);
        ArrayNode arr = objectMapper.createArrayNode();
        for (int i = start; i < list.size(); i++) {
            arr.add(list.get(i));
        }
        saveSetting(KEY_INGESTED, arr.toString(), Instant.now(), updatedBy);
    }

    private Instant readInstant(String key) {
        String raw = readRaw(key, null);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(raw.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private int readInt(String key, int dflt) {
        try {
            return Integer.parseInt(readRaw(key, String.valueOf(dflt)).trim());
        } catch (Exception e) {
            return dflt;
        }
    }

    private String readRaw(String key, String dflt) {
        return systemSettingRepository.findById(key)
            .map(SystemSetting::getSettingValue)
            .filter(v -> v != null && !v.isBlank())
            .orElse(dflt);
    }

    private void saveSetting(String key, String value, Instant now, String updatedBy) {
        SystemSetting setting = systemSettingRepository.findById(key).orElseGet(() ->
            SystemSetting.builder().settingKey(key).build());
        setting.setSettingValue(value);
        setting.setUpdatedAt(now);
        setting.setUpdatedBy(updatedBy != null ? updatedBy : "system");
        systemSettingRepository.save(setting);
    }

    static String extractJsonObject(String raw) {
        if (raw == null || raw.isBlank()) {
            return "{}";
        }
        String t = raw.trim();
        if (t.startsWith("```")) {
            int nl = t.indexOf('\n');
            if (nl > 0) {
                t = t.substring(nl + 1).trim();
            }
            if (t.endsWith("```")) {
                t = t.substring(0, t.length() - 3).trim();
            }
        }
        int start = t.indexOf('{');
        int end = t.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return t.substring(start, end + 1);
        }
        return t;
    }

    static boolean looksLikeLlmError(String s) {
        if (s == null) {
            return true;
        }
        String n = s.toLowerCase();
        return n.contains("credit balance")
            || n.contains("rate_limit")
            || n.contains("rate limit")
            || n.contains("i'm claude")
            || n.contains("as an ai");
    }

    public int drillsToday(Instant now) {
        Instant at = now != null ? now : Instant.now();
        java.time.LocalDate day = at.atZone(KST).toLocalDate();
        Instant start = day.atStartOfDay(KST).toInstant();
        Instant end = day.plusDays(1).atStartOfDay(KST).toInstant();
        return (int) exampleRepository.countBySourceAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            XPersonaExample.Source.DRILL, start, end);
    }

    public LearnResult requireEnabledThenRun(String updatedBy) {
        if (!xOpsSettingsService.get().personaLearningEnabled()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "persona learning is disabled");
        }
        return runNow(updatedBy);
    }
}
