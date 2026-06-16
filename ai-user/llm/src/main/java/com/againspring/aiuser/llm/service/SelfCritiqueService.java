package com.againspring.aiuser.llm.service;

import com.againspring.aiuser.llm.pool.LlmWorkerPool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
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

        boolean passed = score >= passThreshold;
        log.debug("quickCheck type={} score={}/{} passed={} issues={}", contentType, score, 7, passed, issues);
        return new CritiqueResult(passed, score, issues);
    }

    /**
     * 자기비평 + 재생성.
     * quickCheck FAIL 시 비평 결과를 포함한 재생성 프롬프트로 1회 재시도.
     * 재시도도 빈 텍스트이면 원본 반환 (graceful fallback).
     * backend: 원래 요청과 동일한 backend("CLI"|"API"|null) — 설정 일관성 유지.
     * formality: "casual" (반말) | "polite" (존댓말) | null (기본값)
     */
    public String critiqueAndRefine(String draft, String contentType, String originalPrompt, String corrId, String backend) {
        return critiqueAndRefine(draft, contentType, originalPrompt, corrId, backend, null);
    }

    /**
     * 자기비평 + 재생성 (formality 고려).
     */
    public String critiqueAndRefine(String draft, String contentType, String originalPrompt, String corrId, String backend, String formality) {
        return critiqueAndRefine(draft, contentType, originalPrompt, corrId, backend, formality, null, null);
    }

    /**
     * 자기비평 + 재생성 (formality·model 고려). model=null이면 풀 기본 모델.
     */
    public String critiqueAndRefine(String draft, String contentType, String originalPrompt, String corrId,
                                    String backend, String formality, String model) {
        return critiqueAndRefine(draft, contentType, originalPrompt, corrId, backend, formality, model, null);
    }

    /**
     * 자기비평 + 재생성 (formality·model·voiceType 고려). model/voiceType=null이면 기본값.
     */
    public String critiqueAndRefine(String draft, String contentType, String originalPrompt, String corrId,
                                    String backend, String formality, String model, String voiceType) {
        if (!enabled || draft == null || draft.isBlank()) return draft;

        CritiqueResult result = quickCheck(draft, contentType, formality);
        if (result.passed()) {
            log.debug("critique PASS corr={} score={}", corrId, result.score());
            return draft;
        }

        log.info("critique FAIL corr={} score={} issues={} → retrying", corrId, result.score(), result.issues());

        // 재생성 프롬프트: 원본 + 비평 피드백 주입
        String retryPrompt = buildRetryPrompt(originalPrompt, draft, result.issues());

        try {
            String raw = pool.executeSyncTask(retryPrompt, model, 90000L, corrId + "-retry", backend);
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

    private String buildRetryPrompt(String originalPrompt, String draft, List<String> issues) {
        String issueText = String.join(", ", issues);

        // 반말/존댓말 위반에 대한 상세 지시 추가
        String issueDetail = issueText.contains("반말 위반")
            ? issueText + " — ~요/~어요/~했어요로 끝나는 모든 문장을 ~음/~임/~더라/~잖아/~거든 류 반말로 바꿔라"
            : issueText.contains("존댓말 어미 단조")
            ? issueText + " — 매 문장마다 다른 종결어미 사용(~요 / ~더라고요 / ~거든요 / ~네요 / 명사종결 등 혼용)"
            : issueText;

        // system 부분 유지, user 부분에 피드백 추가
        String sep = "<<<USER_PROMPT>>>";
        if (originalPrompt.contains(sep)) {
            String[] parts = originalPrompt.split(sep, 2);
            String system = parts[0];
            String user = parts.length > 1 ? parts[1] : "";
            String retryUser = "[수정 요청] 아래 글에서 다음 문제를 수정해 다시 써라: " + issueDetail +
                               "\n원문:\n" + draft.substring(0, Math.min(draft.length(), 400)) +
                               "\n\n원래 요청:\n" + user;
            return system + "\n" + sep + "\n" + retryUser;
        }
        // 구분자 없으면 그냥 붙임
        return originalPrompt + "\n\n[수정 요청] 다음 문제 수정: " + issueDetail + "\n원문: " + draft.substring(0, Math.min(300, draft.length()));
    }
}
