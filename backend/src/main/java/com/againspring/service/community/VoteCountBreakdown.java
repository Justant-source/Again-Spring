package com.againspring.service.community;

/**
 * 투표 카운트 분리 결과 (사람/AI 구분)
 */
public class VoteCountBreakdown {
    public long humanCount;
    public long aiCount;

    public VoteCountBreakdown(long humanCount, long aiCount) {
        this.humanCount = humanCount;
        this.aiCount = aiCount;
    }

    public long getTotalCount() {
        return humanCount + aiCount;
    }
}
