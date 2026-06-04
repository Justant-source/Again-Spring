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

    public record CritiqueResult(boolean passed, int score, List<String> issues) {}

    // ── 빠른 결정론적 체크 (LLM 호출 전 0비용) ─────────────────────

    private static final Pattern PERIOD_AT_EOL   = Pattern.compile("(?<![.?!])\\. *(\n|$)");
    private static final Pattern DOUBLE_QUOTE     = Pattern.compile("\"[^\"\\n]{1,60}\"");
    private static final Pattern REPEATED_ENDING  = Pattern.compile("(다들 어떻게|어떻게 해야 함\\?|어떻게 해야 할까)");
    private static final Pattern PERFECT_STRUCTURE = Pattern.compile("(?s)(배경|상황).*갈등.*질문|도입.*사건.*갈등.*질문");
    private static final Pattern EMOTION_TELL     = Pattern.compile("(?:서운함|답답함|배신감|억울함|분노|불안감|자존감 하락|허탈함)[이가이을를]?");

    /**
     * 결정론적(0비용) 빠른 체크. LLM 호출 없이 점수 산출.
     * 점수가 passThreshold-1 이하일 때만 LLM 비평 호출.
     */
    public CritiqueResult quickCheck(String text, String contentType) {
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

        // 5. 종결어미 단조로움: 모든 문장이 ~임/~함으로만 끝남
        String[] lines = text.split("[\\n\\r]+");
        int totalLines = 0, uniformEnding = 0;
        for (String line : lines) {
            String t = line.trim();
            if (t.length() > 5) {
                totalLines++;
                if (t.endsWith("임") || t.endsWith("함") || t.endsWith("됨") || t.endsWith("있음") || t.endsWith("없음")) {
                    uniformEnding++;
                }
            }
        }
        if (totalLines >= 4 && uniformEnding * 100 / totalLines > 80) {
            score -= 1;
            issues.add("종결어미 단조로움");
        }

        boolean passed = score >= passThreshold;
        log.debug("quickCheck type={} score={}/{} passed={} issues={}", contentType, score, 7, passed, issues);
        return new CritiqueResult(passed, score, issues);
    }

    /**
     * 자기비평 + 재생성.
     * quickCheck FAIL 시 비평 결과를 포함한 재생성 프롬프트로 1회 재시도.
     * 재시도도 빈 텍스트이면 원본 반환 (graceful fallback).
     */
    public String critiqueAndRefine(String draft, String contentType, String originalPrompt, String corrId) {
        if (!enabled || draft == null || draft.isBlank()) return draft;

        CritiqueResult result = quickCheck(draft, contentType);
        if (result.passed()) {
            log.debug("critique PASS corr={} score={}", corrId, result.score());
            return draft;
        }

        log.info("critique FAIL corr={} score={} issues={} → retrying", corrId, result.score(), result.issues());

        // 재생성 프롬프트: 원본 + 비평 피드백 주입
        String retryPrompt = buildRetryPrompt(originalPrompt, draft, result.issues());

        try {
            String raw = pool.executeSyncTask(retryPrompt, null, 90000L, corrId + "-retry");
            String refined = "post".equalsIgnoreCase(contentType)
                ? outputSanitizer.sanitizePost(raw)
                : outputSanitizer.sanitizeComment(raw);

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

    private String buildRetryPrompt(String originalPrompt, String draft, List<String> issues) {
        String issueText = String.join(", ", issues);
        // system 부분 유지, user 부분에 피드백 추가
        String sep = "<<<USER_PROMPT>>>";
        if (originalPrompt.contains(sep)) {
            String[] parts = originalPrompt.split(sep, 2);
            String system = parts[0];
            String user = parts.length > 1 ? parts[1] : "";
            String retryUser = "[수정 요청] 아래 글에서 다음 문제를 수정해 다시 써라: " + issueText +
                               "\n원문:\n" + draft.substring(0, Math.min(draft.length(), 400)) +
                               "\n\n원래 요청:\n" + user;
            return system + "\n" + sep + "\n" + retryUser;
        }
        // 구분자 없으면 그냥 붙임
        return originalPrompt + "\n\n[수정 요청] 다음 문제 수정: " + issueText + "\n원문: " + draft.substring(0, Math.min(300, draft.length()));
    }
}
