package com.againspring.aiuser.llm.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 마케팅 캡처 분할 컷을 본문에서 결정론적으로 계산한다(LLM에게 계산을 시키지 않는다).
 *
 * <p>캡처 카드는 본문 폭 338px·15px 글자라 줄당 약 {@value #CHARS_PER_VISUAL_LINE}자다.
 * 블록(비어 있지 않은 줄)의 무게 = 시각 줄 수 = ceil(글자수/22). 한 파트 예산 = 시각 줄
 * {@value #PART_LINE_BUDGET}줄 AND 블록 {@value #SHORT_POST_MAX_BLOCKS}개 이하.
 * 컷 없음(null) = 블록 ≤ 8 AND 가중합 ≤ 24. 그 밖에는 필요한 최소 파트 수(최대 {@value #MAX_PARTS})에서
 * 가장 무거운 파트의 가중합이 최소가 되도록 컷을 고른다. 한 블록이 단독으로 24줄을 넘으면 그 블록만으로 한 파트가 된다.
 *
 * <p>컷은 1-based, "마지막 파트를 제외한 각 파트의 마지막 블록 인덱스"(backend CaptureSplitSupport 계약).
 * 최종 결정권은 backend에 있고 이 값은 제안값이다. 예산을 어떻게 해도 못 맞추면(4파트 초과 분량)
 * 블록 수 제약만 지키는 분할로 완화하고, 그것도 불가능하면(블록 32개 초과) null을 돌려 backend 휴리스틱에 맡긴다.
 */
public final class CaptureSplitPlanner {
    public static final int SHORT_POST_MAX_BLOCKS = 8;
    public static final int MAX_PARTS = 4;
    public static final int CHARS_PER_VISUAL_LINE = 22;
    public static final int PART_LINE_BUDGET = 24;

    private CaptureSplitPlanner() {}

    /** 블록 하나의 시각 줄 수(최소 1). */
    static int weight(String block) {
        int len = block.strip().length();
        return Math.max(1, (len + CHARS_PER_VISUAL_LINE - 1) / CHARS_PER_VISUAL_LINE);
    }

    /** @return 1-based 컷 목록 또는 null(분할 불필요/불가). */
    public static List<Integer> plan(String body) {
        if (body == null || body.isBlank()) return null;
        List<Integer> w = new ArrayList<>();
        for (String line : body.split("\\R", -1)) {
            if (!line.isBlank()) w.add(weight(line));
        }
        int n = w.size();
        long total = 0;
        for (int x : w) total += x;
        if (n <= SHORT_POST_MAX_BLOCKS && total <= PART_LINE_BUDGET) return null;
        if (n > SHORT_POST_MAX_BLOCKS * MAX_PARTS) return null;

        long[] prefix = new long[n + 1];
        for (int i = 0; i < n; i++) prefix[i + 1] = prefix[i] + w.get(i);

        for (boolean strictLines : new boolean[]{true, false}) {
            for (int parts = 1; parts <= MAX_PARTS; parts++) {
                List<Integer> cuts = solve(prefix, n, parts, strictLines);
                if (cuts != null) return cuts.isEmpty() ? null : List.copyOf(cuts);
            }
        }
        return null;
    }

    /** parts개 파트로 가장 무거운 파트를 최소화(DP). 불가능하면 null. */
    private static List<Integer> solve(long[] prefix, int n, int parts, boolean strictLines) {
        final long INF = Long.MAX_VALUE / 4;
        long[][] dp = new long[parts + 1][n + 1];
        int[][] from = new int[parts + 1][n + 1];
        for (long[] row : dp) Arrays.fill(row, INF);
        dp[0][0] = 0;
        for (int k = 1; k <= parts; k++) {
            for (int i = k; i <= n; i++) {
                for (int size = 1; size <= SHORT_POST_MAX_BLOCKS && size <= i; size++) {
                    int j = i - size;
                    if (dp[k - 1][j] >= INF) continue;
                    long sum = prefix[i] - prefix[j];
                    if (strictLines && size > 1 && sum > PART_LINE_BUDGET) continue; // 단독 블록은 예외
                    long cost = Math.max(dp[k - 1][j], sum);
                    if (cost < dp[k][i]) { dp[k][i] = cost; from[k][i] = j; }
                }
            }
        }
        if (dp[parts][n] >= INF) return null;
        List<Integer> cuts = new ArrayList<>();
        int i = n;
        for (int k = parts; k >= 2; k--) {
            i = from[k][i];
            cuts.add(0, i);
        }
        return cuts;
    }
}
