package com.againspring.service.community;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * 광장 검색 V1(슬라이스 ①) 쿼리 정규화 헬퍼.
 *
 * <ul>
 *   <li>매칭: {@code post_search_ngrams} 문자 바이그램 AND (미색인 글 LIKE 폴백)</li>
 *   <li>Exact 티어: 정규화 쿼리 전체가 제목에 연속 포함</li>
 *   <li>인기×감쇠: {@code (2*votes + comments + 1) * max(0.05, 0.5^(age/14d))} — SQL에서 계산</li>
 * </ul>
 */
public final class PostSearchQuery {

    public static final int MIN_QUERY_LENGTH = 2;
    public static final int MAX_TOKENS = 8;
    public static final char LIKE_ESCAPE = '!';
    /** 반감기(초). 14일. */
    public static final double HALF_LIFE_SECONDS = 14.0 * 24 * 3600;
    public static final double DECAY_FLOOR = 0.05;

    private PostSearchQuery() {}

    /** trim + 내부 공백 붕괴. */
    public static String normalize(String raw) {
        if (raw == null) return "";
        return raw.trim().replaceAll("\\s+", " ");
    }

    public static boolean isTooShort(String normalized) {
        return normalized.codePointCount(0, normalized.length()) < MIN_QUERY_LENGTH;
    }

    /** 공백 기준 토큰 (최대 {@link #MAX_TOKENS}). */
    public static List<String> tokens(String normalized) {
        if (normalized.isEmpty()) return List.of();
        return Arrays.stream(normalized.split(" "))
                .filter(t -> !t.isBlank())
                .limit(MAX_TOKENS)
                .toList();
    }

    /** LIKE ESCAPE '!' 용 이스케이프. */
    public static String escapeLike(String raw) {
        return raw
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
    }

    /** {@code %escaped%} 패턴. */
    public static String containsPattern(String raw) {
        return "%" + escapeLike(raw) + "%";
    }

    /** 단위 테스트·문서용 감쇠식 (SQL과 동일 의미). */
    public static double timeDecay(double ageSeconds) {
        if (ageSeconds <= 0) return 1.0;
        double decay = Math.pow(0.5, ageSeconds / HALF_LIFE_SECONDS);
        return Math.max(DECAY_FLOOR, decay);
    }

    public static double popularityScore(long votes, long comments) {
        return 2.0 * votes + comments + 1.0;
    }

    public static String describeForLog(String normalized, String category) {
        return "q='" + normalized + "' category=" + (category == null ? "-" : category.toUpperCase(Locale.ROOT));
    }
}
