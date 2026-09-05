package com.againspring.aiuser.orchestrator.safety;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

/**
 * persona-diversity-v4 WP2 — 재구성 결과가 크롤 원문을 그대로 옮기지 않았는지 검사한다.
 * {@code ai-user/learning/app/services/ngram_guard.py}와 동일 정의: 공백 정규화 후 문자 12-gram,
 * {@code overlap = |gen ∩ src| / |gen|}, 임계 0.20 초과면 거부.
 *
 * <p><b>배선 상태</b>: 이 클래스는 구현·단위테스트만 완료했다. 문서(item5)가 지정한 실제 호출
 * 지점(AiPostBundleService 홀딩 직전)은 WP2 소유 편집 범위(`:625-640` 요청 조립 구간)
 * 밖이라 이 브랜치에서는 배선하지 않았다 — 다른 WP 에이전트가 같은 파일의 다른 구간을
 * 동시에 수정 중이므로, 병합 시점에 호출부를 붙여야 한다.</p>
 */
@Slf4j
@Component
public class SourceOverlapGuard {

    public static final int MIN_GRAM = 12;
    public static final double THRESHOLD = 0.20;
    public static final String REASON = "SOURCE_NGRAM_OVERLAP";

    public record GuardResult(boolean passed, String reason, double overlapRatio) {
        public static GuardResult ok(double ratio) {
            return new GuardResult(true, null, ratio);
        }

        public static GuardResult blocked(double ratio) {
            return new GuardResult(false, REASON, ratio);
        }
    }

    /**
     * @param generatedText 재구성된 글/댓글 본문(제목 포함 시 title+body concat 권장)
     * @param rawSource     원본 크롤 본문(게시·로그 저장 금지 — 메모리에서만 비교 후 버릴 것)
     */
    public GuardResult check(String generatedText, String rawSource) {
        if (generatedText == null || generatedText.isBlank() || rawSource == null || rawSource.isBlank()) {
            return GuardResult.ok(0.0);
        }
        double ratio = overlapRatio(generatedText, rawSource, MIN_GRAM);
        if (ratio > THRESHOLD) {
            log.error("SourceOverlapGuard reject: overlapRatio={} > threshold={}", ratio, THRESHOLD);
            return GuardResult.blocked(ratio);
        }
        return GuardResult.ok(ratio);
    }

    /**
     * ngram_guard.py {@code overlap_ratio}와 동일 결과를 내는 O(n) 구현.
     * 길이 {@code minGram} 이상으로 일치하는 모든 구간은, 그 안에 포함된 모든 minGram 길이
     * 부분문자열도 원문에 실제로 존재하므로(연속 부분문자열의 부분문자열), minGram 윈도우
     * 단위로 원문에 존재하는지만 검사해 커버리지를 구해도 동일한 합집합이 나온다.
     */
    static double overlapRatio(String generated, String original, int minGram) {
        String gen = normalize(generated);
        String orig = normalize(original);
        if (gen.isEmpty() || orig.isEmpty() || gen.length() < minGram) return 0.0;

        Set<String> origGrams = new HashSet<>();
        for (int i = 0; i + minGram <= orig.length(); i++) {
            origGrams.add(orig.substring(i, i + minGram));
        }
        if (origGrams.isEmpty()) return 0.0;

        boolean[] covered = new boolean[gen.length()];
        for (int i = 0; i + minGram <= gen.length(); i++) {
            String window = gen.substring(i, i + minGram);
            if (origGrams.contains(window)) {
                for (int j = i; j < i + minGram; j++) covered[j] = true;
            }
        }
        int coveredCount = 0;
        for (boolean b : covered) if (b) coveredCount++;
        return (double) coveredCount / gen.length();
    }

    /** 연속 공백 축약, 개행→공백, 트림 — ngram_guard.py {@code _normalize_text_for_comparison}과 동일. */
    static String normalize(String text) {
        if (text == null) return "";
        String t = text.replace("\r\n", " ").replace("\r", " ").replace("\n", " ");
        t = t.replaceAll("[ \\t]+", " ");
        return t.trim();
    }
}
