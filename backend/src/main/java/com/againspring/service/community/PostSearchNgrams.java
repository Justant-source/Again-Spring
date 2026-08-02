package com.againspring.service.community;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * MariaDB ngram FULLTEXT 대체: 문자 바이그램 추출.
 * <p>
 * MySQL {@code WITH PARSER ngram}은 MariaDB에 없으므로(MDEV-10267),
 * 공백으로 나뉜 토큰마다 유니코드 코드포인트 바이그램을 뽑아 BTREE 테이블에 적재한다.
 */
public final class PostSearchNgrams {

    /** 본문에서 인덱싱할 최대 문자 수(코드포인트 단위에 가깝게 length 절단). */
    public static final int BODY_CHAR_LIMIT = 4_000;
    public static final int MAX_GRAMS_PER_POST = 2_500;
    public static final int MAX_QUERY_GRAMS = 24;

    private PostSearchNgrams() {}

    /** 제목+본문에서 검색 인덱스용 유니크 바이그램. */
    public static Set<String> extractForPost(String title, String bodyPublished) {
        Set<String> out = new LinkedHashSet<>();
        addFromText(title, out);
        if (out.size() >= MAX_GRAMS_PER_POST) return out;
        String body = bodyPublished == null ? "" : bodyPublished;
        if (body.length() > BODY_CHAR_LIMIT) {
            body = body.substring(0, BODY_CHAR_LIMIT);
        }
        addFromText(body, out);
        return out;
    }

    /** 정규화된 검색어 → AND에 쓸 바이그램 목록(순서 유지, 중복 제거, 상한). */
    public static List<String> extractForQuery(String normalizedQuery) {
        Set<String> out = new LinkedHashSet<>();
        addFromText(normalizedQuery, out);
        List<String> list = new ArrayList<>(out);
        if (list.size() > MAX_QUERY_GRAMS) {
            return list.subList(0, MAX_QUERY_GRAMS);
        }
        return list;
    }

    private static void addFromText(String text, Set<String> out) {
        if (text == null || text.isBlank()) return;
        for (String token : text.trim().split("\\s+")) {
            if (token.isEmpty()) continue;
            addBigrams(token, out);
            if (out.size() >= MAX_GRAMS_PER_POST) return;
        }
    }

    private static void addBigrams(String token, Set<String> out) {
        int[] cps = token.codePoints().toArray();
        if (cps.length < 2) return;
        if (cps.length == 2) {
            out.add(new String(cps, 0, 2));
            return;
        }
        for (int i = 0; i < cps.length - 1; i++) {
            out.add(new String(cps, i, 2));
            if (out.size() >= MAX_GRAMS_PER_POST) return;
        }
    }
}
