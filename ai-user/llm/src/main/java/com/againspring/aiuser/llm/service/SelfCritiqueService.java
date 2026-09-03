package com.againspring.aiuser.llm.service;

import com.againspring.aiuser.llm.pool.LlmWorkerPool;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 생성된 텍스트에 대한 자기비평(self-critique) 서비스.
 * KatFishNet 기반 6개 체크포인트로 AI 냄새 탐지 → 재생성 여부 결정.
 *
 * 비용 통제: POST/COMMENT만 적용, REPLY 제외 (짧아서 필요 없음).
 * graceful fallback: 비평/재생성 실패 시 원본 반환.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SelfCritiqueService {

    private final LlmWorkerPool pool;
    private final PromptAssembler promptAssembler;
    private final OutputSanitizer outputSanitizer;

    @Value("${self-critique.enabled:false}")
    private boolean enabled;

    @Value("${self-critique.pass-threshold:5}")
    private int passThreshold;   // 7점 만점에서 이 점수 이상이면 PASS

    /** 추가 상투구 (쉼표 구분, 리터럴 매칭) — 운영 중 발견한 AI투를 재배포 없이 등록. */
    @Value("${self-critique.extra-cliches:}")
    private String extraCliches;
    private volatile Pattern extraClichePattern;

    // ── 어휘이질(T5) 탐지: 희귀/문어체 어휘 비율 detector ──────────────────────────────────
    /** 다크 출시: 캘리브레이션 후 SELF_CRITIQUE_RARE_VOCAB_ENABLED=true로 활성화 */
    @Value("${self-critique.rare-vocab.enabled:false}")
    private boolean rareVocabEnabled;

    @Value("${self-critique.rare-vocab.ratio-threshold:0.18}")
    private double rareRatioThreshold;

    @Value("${self-critique.rare-vocab.min-tokens:25}")
    private int rareMinTokens;

    @Value("${self-critique.rare-vocab.penalty:1}")
    private int rareVocabPenalty;

    private volatile Set<String> commonWords = Collections.emptySet();

    public record CritiqueResult(boolean passed, int score, List<String> issues) {}

    // ── 빠른 결정론적 체크 (LLM 호출 전 0비용) ─────────────────────

    private static final Pattern PERIOD_AT_EOL   = Pattern.compile("(?<![.?!])\\. *(\n|$)");
    private static final Pattern DOUBLE_QUOTE     = Pattern.compile("\"[^\"\\n]{1,60}\"");
    private static final Pattern REPEATED_ENDING  = Pattern.compile("(다들 어떻게|어떻게 해야 함\\?|어떻게 해야 할까)");
    private static final Pattern PERFECT_STRUCTURE = Pattern.compile("(?s)(배경|상황).*갈등.*질문|도입.*사건.*갈등.*질문");
    private static final Pattern EMOTION_TELL     = Pattern.compile("(?:서운함|답답함|배신감|억울함|분노|불안감|자존감 하락|허탈함)[이가이을를]?");

    // ── AI투 상투구 체크 (문체 현실화 S4) ────────────────────────────────
    /** 상담원·응원 멘트 — 실제 커뮤니티에선 거의 안 쓰는 강한 AI 시그널. */
    private static final Pattern AI_CLICHE = Pattern.compile(
        "정말 공감|공감되네요|공감됩니다|힘내세요|응원합니다|응원할게요|응원해요|" +
        "마음이 느껴|충분히 .{0,6}(?:할 수|이해)|그렇군요|좋은 결과 있|기원합니다|화이팅!");
    /** 강조어 남발 감지 — "진짜"·"정말" 합산. */
    private static final Pattern EMPHASIS_WORD = Pattern.compile("진짜|정말");
    /** ㅠ/ㅜ 묶음 — 3회 이상이면 남발. */
    private static final Pattern SOB_RUN = Pattern.compile("[ㅠㅜ]+");

    // ── 구조적 AI투 후보 (2026-09-02, 로그 전용 — 점수 미반영) ──────────────
    // 로컬 블라인드 코퍼스(.history/.result)에 AI/사람 라벨이 없어 사전 캘리브레이션 불가.
    // score/issues에 반영하지 않고 log.debug만 남긴다. 실제 생성물 로그가 쌓이면 재검토.
    // "결국"은 코퍼스에서 8건 모두 문단 중간 인과 접속사로만 나타나므로 제외.
    private static final Pattern CLOSING_SUMMARY_MARKER =
        Pattern.compile("^(?:정리하자면|요약하자면|결론적으로|결론은)[,\\s]");
    private static final Pattern SYMMETRIC_CONTRAST = Pattern.compile("아니라");

    /** 마지막 문단(빈 줄 기준 분리)이 요약 표지로 시작하는지. */
    static boolean hasClosingSummaryParagraph(String text) {
        if (text == null || text.isBlank()) return false;
        String[] paragraphs = text.strip().split("\\n\\s*\\n");
        String last = paragraphs[paragraphs.length - 1].trim();
        return CLOSING_SUMMARY_MARKER.matcher(last).find();
    }

    /** "아니라" 등장 횟수 — 2회 이상이면 대칭 대조 남용 후보. */
    static int countSymmetricContrast(String text) {
        if (text == null) return 0;
        java.util.regex.Matcher m = SYMMETRIC_CONTRAST.matcher(text);
        int n = 0;
        while (m.find()) n++;
        return n;
    }

    // formality 체크용 패턴
    private static final Pattern POLITE_ENDING = Pattern.compile(
        "(?:(?:했|했었|었|이었|이)어요|(?:했|했었|었|이었|이)?에요|(?:했|해)요|습니다|니다|세요|예요)\\s*$",
        Pattern.MULTILINE);
    private static final Pattern SAME_ENDING_SEQ = Pattern.compile(
        "(?:어요|아요|했어요|었어요|이에요|예요)[^\\S\\n]*(?:\\n|$).*?(?:어요|아요|했어요|었어요|이에요|예요)[^\\S\\n]*(?:\\n|$)",
        Pattern.DOTALL);

    /**
     * 결정론적(0비용) 빠른 체크. LLM 호출 없이 점수 산출.
     * 점수가 passThreshold-1 이하일 때만 LLM 비평 호출.
     */
    public CritiqueResult quickCheck(String text, String contentType) {
        return quickCheck(text, contentType, null);
    }

    /**
     * 결정론적(0비용) 빠른 체크 (formality 고려).
     * formality: "casual" (반말) | "polite" (존댓말) | null (기본값)
     */
    public CritiqueResult quickCheck(String text, String contentType, String formality) {
        if (!enabled || text == null || text.isBlank()) {
            return new CritiqueResult(true, 7, List.of());
        }

        List<String> issues = new ArrayList<>();
        int score = 7; // 7점 만점 시작

        // 1. 온점(.) 사용 — 커뮤니티에선 거의 안 씀
        if (PERIOD_AT_EOL.matcher(text).find()) {
            score -= 2;
            issues.add("온점 사용");
        }

        // 2. 쌍따옴표 간접화법
        if (DOUBLE_QUOTE.matcher(text).find()) {
            score -= 2;
            issues.add("쌍따옴표 사용");
        }

        // 3. 반복적 마무리 질문
        if (REPEATED_ENDING.matcher(text).find()) {
            score -= 1;
            issues.add("마무리 패턴 반복");
        }

        // 4. 감정 추상명사 직접 서술 (tell, not show)
        if (EMOTION_TELL.matcher(text).find()) {
            score -= 1;
            issues.add("감정 추상명사 직접 서술");
        }

        // 8. AI 상투구 — 상담원·응원 멘트 (문체 현실화 S4)
        if (AI_CLICHE.matcher(text).find() || matchesExtraCliche(text)) {
            score -= 2;
            issues.add("AI 상투구(공감되네요/힘내세요/응원합니다 류) — 구체적인 자기 말로 교체");
        }

        // 9. 강조어 남발 — 진짜/정말 합산 3회 이상
        int emphasis = countMatches(EMPHASIS_WORD, text);
        if (emphasis >= 3) {
            score -= 1;
            issues.add("강조어 남발(진짜/정말 " + emphasis + "회) — 아예/완전/걍 등으로 변주");
        }

        // 10. ㅠ 남발 — ㅠ/ㅜ 묶음 3회 이상
        int sobRuns = countMatches(SOB_RUN, text);
        if (sobRuns >= 3) {
            score -= 1;
            issues.add("ㅠ 남발(" + sobRuns + "회) — 일부 제거하거나 다른 종결로");
        }

        // 11. 쉼표 과다 (AI 실측 5%↑, 인간 베이스라인 최대 3%) — 커뮤니티 공통
        long commaCount = text.chars().filter(c -> c == ',').count();
        if (!text.isEmpty() && (double) commaCount / text.length() > 0.05) {
            score -= 1;
            issues.add("쉼표 과다(AI 투) — 쉼표를 2/3 이상 제거하고 다시 쓸 것");
        }

        // 12. 어휘이질(T5) — 희귀/문어체 어휘 비율 과다. POST에만, 충분히 긴 글만 적용.
        if (rareVocabEnabled && !commonWords.isEmpty()
                && "post".equalsIgnoreCase(contentType)) {
            java.util.List<String> toks = tokenizeForRareVocab(text);
            if (toks.size() >= rareMinTokens) {
                int rare = 0;
                for (String tk : toks) if (!commonWords.contains(tk)) rare++;
                double ratio = (double) rare / toks.size();
                if (ratio > rareRatioThreshold) {
                    score -= rareVocabPenalty;
                    issues.add("어휘이질(평범한 사람이 안 쓰는 문어체·고급 어휘 과다, rare " +
                               Math.round(ratio * 100) + "%) — 더 일상적이고 평범한 단어로 바꿔라");
                }
            }
        }

        // 5. 종결어미 단조로움: casual 모드에서만 ~임/~함 단조 체크
        String[] lines = text.split("[\\n\\r]+");
        int totalLines = 0, uniformEnding = 0;
        boolean isCasual = !"polite".equalsIgnoreCase(formality);
        for (String line : lines) {
            String t = line.trim();
            if (t.length() > 5) {
                totalLines++;
                if (t.endsWith("임") || t.endsWith("함") || t.endsWith("됨") || t.endsWith("있음") || t.endsWith("없음")) {
                    uniformEnding++;
                }
            }
        }
        if (isCasual && totalLines >= 4 && uniformEnding * 100 / totalLines > 80) {
            score -= 1;
            issues.add("종결어미 단조로움(~임/~함 과다)");
        }

        // 6. casual 모드인데 존댓말 어미 사용 — 큰 감점
        if (isCasual && formality != null) {
            long politeLines = 0L;
            long checkedLines = 0L;
            for (String line : lines) {
                String t = line.trim();
                if (t.length() > 5) {
                    checkedLines++;
                    if (POLITE_ENDING.matcher(t).find()) {
                        politeLines++;
                    }
                }
            }
            if (checkedLines > 0 && politeLines > 0) {
                score -= 3;
                issues.add("반말 위반(~요/~어요 사용) — ~음/~임/~더라 류 반말로 고쳐라");
            }
        }

        // 7. polite 모드 — 어미 단조로움 체크
        if (!isCasual && formality != null) {
            // 연속 같은 어미 체크
            if (SAME_ENDING_SEQ.matcher(text).find()) {
                score -= 1;
                issues.add("존댓말 어미 단조 반복(같은 어미 2연속) — 매 문장 다른 어미 사용");
            }
            // ~어요/~했어요 비율 과다 체크
            long totalEndings = 0L, ayoEndings = 0L;
            for (String line : lines) {
                String t = line.trim();
                if (t.length() > 5) {
                    totalEndings++;
                    if (t.endsWith("어요") || t.endsWith("했어요") || t.endsWith("았어요")) {
                        ayoEndings++;
                    }
                }
            }
            if (totalEndings >= 4 && ayoEndings * 100 / totalEndings > 60) {
                score -= 1;
                issues.add("존댓말 어미 단조(~어요/~했어요 60%↑) — 다른 어미 섞기");
            }
        }

        // 구조적 AI투 후보 — 점수 미반영, 로그만 (캘리브레이션 데이터 수집용)
        if (hasClosingSummaryParagraph(text)) {
            log.debug("[STRUCTURAL_TELL_CANDIDATE] closing-summary-paragraph type={}", contentType);
        }
        int contrastCount = countSymmetricContrast(text);
        if (contrastCount >= 2) {
            log.debug("[STRUCTURAL_TELL_CANDIDATE] symmetric-contrast count={} type={}", contrastCount, contentType);
        }

        boolean passed = score >= passThreshold;
        log.debug("quickCheck type={} score={}/{} passed={} issues={}", contentType, score, 7, passed, issues);
        return new CritiqueResult(passed, score, issues);
    }

    /**
     * 자기비평 + 재생성.
     * quickCheck FAIL 시 비평 결과를 포함한 재생성 프롬프트로 1회 재시도.
     * 재시도도 빈 텍스트이면 원본 반환 (graceful fallback).
     * provider: 원래 요청과 동일한 provider — 설정 일관성 유지.
     * formality: "casual" (반말) | "polite" (존댓말) | null (기본값)
     */
    public String critiqueAndRefine(String draft, String contentType, String originalPrompt, String corrId, LlmProvider provider) {
        return critiqueAndRefine(draft, contentType, originalPrompt, corrId, provider, null);
    }

    /**
     * 자기비평 + 재생성 (formality 고려).
     */
    public String critiqueAndRefine(String draft, String contentType, String originalPrompt, String corrId, LlmProvider provider, String formality) {
        return critiqueAndRefine(draft, contentType, originalPrompt, corrId, provider, formality, null, null);
    }

    /**
     * 자기비평 + 재생성 (formality·model 고려). model=null이면 풀 기본 모델.
     */
    public String critiqueAndRefine(String draft, String contentType, String originalPrompt, String corrId,
                                    LlmProvider provider, String formality, String model) {
        return critiqueAndRefine(draft, contentType, originalPrompt, corrId, provider, formality, model, null);
    }

    /**
     * 자기비평 + 재생성 (formality·model·voiceType 고려). model/voiceType=null이면 기본값.
     */
    public String critiqueAndRefine(String draft, String contentType, String originalPrompt, String corrId,
                                    LlmProvider provider, String formality, String model, String voiceType) {
        if (!enabled || draft == null || draft.isBlank()) return draft;

        CritiqueResult result = quickCheck(draft, contentType, formality);
        if (result.passed()) {
            log.debug("critique PASS corr={} score={}", corrId, result.score());
            return draft;
        }

        log.info("critique FAIL corr={} score={} issues={} → retrying", corrId, result.score(), result.issues());
        log.info("[LLMSTATS] type=CRITIQUE retryReason=CRITIQUE_FAIL score={} corr={}", result.score(), corrId);

        String retryPrompt = buildRetryPrompt(draft, result.issues(), contentType, formality);

        try {
            String raw = pool.executeSyncTask(retryPrompt, model, 90000L, corrId + "-retry", provider);
            String refined = "post".equalsIgnoreCase(contentType)
                ? outputSanitizer.sanitizePost(raw, voiceType)
                : outputSanitizer.sanitizeComment(raw, voiceType);

            if (refined != null && !refined.isBlank()) {
                log.info("critique refined corr={} originalLen={} refinedLen={}", corrId, draft.length(), refined.length());
                return refined;
            }
        } catch (Exception e) {
            log.warn("critique retry failed corr={}: {} — returning original", corrId, e.getMessage());
        }

        // fallback: 원본 반환
        return draft;
    }

    private int countMatches(Pattern p, String text) {
        java.util.regex.Matcher m = p.matcher(text);
        int n = 0;
        while (m.find()) n++;
        return n;
    }

    /** /lexicon/common_words.txt (classpath) → common stem Set. rareVocabEnabled=false면 skip. */
    @PostConstruct
    void loadCommonWords() {
        if (!rareVocabEnabled) {
            log.info("rare-vocab detector disabled — skipping common_words load");
            return;
        }
        Set<String> set = new HashSet<>(8192);
        try (InputStream is = getClass().getResourceAsStream("/lexicon/common_words.txt")) {
            if (is == null) {
                log.warn("common_words.txt not found on classpath — rare-vocab detector inert");
                return;
            }
            try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    String w = line.trim();
                    if (!w.isEmpty()) set.add(w);
                }
            }
            commonWords = Collections.unmodifiableSet(set);
            log.info("rare-vocab: loaded {} common stems", set.size());
        } catch (Exception e) {
            log.warn("rare-vocab: failed to load common_words.txt: {} — detector inert", e.getMessage());
        }
    }

    /** extra-cliches 프로퍼티(쉼표 구분 리터럴) 매칭 — 최초 사용 시 컴파일 후 캐시. */
    private boolean matchesExtraCliche(String text) {
        if (extraCliches == null || extraCliches.isBlank()) return false;
        Pattern p = extraClichePattern;
        if (p == null) {
            String joined = java.util.Arrays.stream(extraCliches.split(","))
                .map(String::trim).filter(s -> !s.isEmpty())
                .map(Pattern::quote)
                .collect(java.util.stream.Collectors.joining("|"));
            if (joined.isEmpty()) return false;
            p = Pattern.compile(joined);
            extraClichePattern = p;
        }
        return p.matcher(text).find();
    }

    /**
     * Short rewrite only — never re-attach the original thread-plan / source / cast prompt.
     * {@code originalPrompt} is kept on the public API for callers but ignored here.
     */
    String buildRetryPrompt(String draft, List<String> issues, String contentType, String formality) {
        String issueText = String.join(", ", issues);

        String issueDetail = issueText.contains("반말 위반")
            ? issueText + " — ~요/~어요/~했어요로 끝나는 모든 문장을 ~음/~임/~더라/~잖아/~거든 류 반말로 바꿔라"
            : issueText.contains("존댓말 어미 단조")
            ? issueText + " — 매 문장마다 다른 종결어미 사용(~요 / ~더라고요 / ~거든요 / ~네요 / 명사종결 등 혼용)"
            : issueText;

        String kind = "post".equalsIgnoreCase(contentType) ? "사연 본문" : "댓글";
        String register = "polite".equalsIgnoreCase(formality)
            ? "존댓말(해요체)을 유지하라."
            : "casual".equalsIgnoreCase(formality)
            ? "반말만 사용하라. 요/어요 종결을 쓰지 마라."
            : "원문의 반말/존댓말을 유지하라.";

        return """
                아래 %s만 다시 써라. JSON·스키마·페르소나 목록·원본 생성 프롬프트를 출력하지 마라.
                %s
                고칠 문제: %s
                다음도 피하라: 마지막 문단에서 전체를 요약해 마무리하지 마라. "A가 아니라 B다" 식 대조 구문을 반복하지 마라. 비슷한 구조의 문장을 세 개 나란히 나열하지 마라. 별것 아닌 일을 거창한 의미로 포장하지 마라.
                의미·사실·줄바꿈 구조를 유지하고 문제만 고쳐라. 본문만 출력하라.

                [원문]
                %s
                """.formatted(kind, register, issueDetail, draft == null ? "" : draft);
    }

    // ── 어휘이질 탐지용 한국어 토크나이저 (Python build_common_words.py와 동일 알고리즘) ──
    // 패리티 계약: 소문자화→공백분리→edge trim→josa/eomi strip 1패스→한글포함 len≥2만 유지
    // 형태소분석 없음 — 동일 구현을 Python과 Java 양쪽에서 확인(SelfCritiqueServiceRareVocabTest.java).
    private static final java.util.regex.Pattern TV_KEEP = java.util.regex.Pattern.compile("[가-힣0-9a-z]");
    private static final java.util.regex.Pattern TV_HANGUL = java.util.regex.Pattern.compile("[가-힣]");
    private static final String[] TV_SUFFIX_2 = {
        "으로","에서","에게","한테","까지","부터","보다","처럼","만큼","이나",
        "라고","라는","으면","어요","아요","에요","예요","네요","거든","잖아",
        "더라","면서","으니","으며","어서","아서","다가"
    };
    private static final String[] TV_SUFFIX_1 = {
        "은","는","이","가","을","를","에","의","도","만","와","과","로","랑",
        "야","요","음","함","됨","임","고","서","게","며","나","지","네","데","든","걸"
    };

    static java.util.List<String> tokenizeForRareVocab(String text) {
        java.util.List<String> out = new java.util.ArrayList<>();
        if (text == null || text.isEmpty()) return out;
        String t = text.toLowerCase()
            .replace('　', ' ').replace(' ', ' ');
        for (String raw : t.split("[ \\t\\n\\r\\u000b\\u000c]+")) {
            if (raw.isEmpty()) continue;
            // edge trim: 양 끝에서 [가-힣0-9a-z] 외 제거
            int i = 0, j = raw.length();
            while (i < j && !TV_KEEP.matcher(String.valueOf(raw.charAt(i))).matches())   i++;
            while (j > i && !TV_KEEP.matcher(String.valueOf(raw.charAt(j-1))).matches()) j--;
            if (i >= j) continue;
            String tok = raw.substring(i, j);
            // josa/eomi suffix strip (한글 끝 & length≥3)
            if (tok.length() >= 3 && TV_HANGUL.matcher(String.valueOf(tok.charAt(tok.length()-1))).matches()) {
                outer:
                for (String s : TV_SUFFIX_2) {
                    if (tok.endsWith(s) && tok.length() - 2 >= 2) { tok = tok.substring(0, tok.length() - 2); break outer; }
                }
                if (tok.length() >= 3 && TV_HANGUL.matcher(String.valueOf(tok.charAt(tok.length()-1))).matches()) {
                    for (String s : TV_SUFFIX_1) {
                        if (tok.endsWith(s) && tok.length() - 1 >= 2) { tok = tok.substring(0, tok.length() - 1); break; }
                    }
                }
            }
            if (tok.length() >= 2 && TV_HANGUL.matcher(tok).find()) out.add(tok);
        }
        return out;
    }
}
