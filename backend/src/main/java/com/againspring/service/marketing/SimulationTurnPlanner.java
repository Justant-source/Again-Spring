package com.againspring.service.marketing;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 마케팅 시뮬레이션 턴 수 결정 컴포넌트.
 * 최소 7턴, 최대 12턴. 텍스트 누적량 및 중재자 마무리 신호로 조기 종료.
 */
@Component
@ConditionalOnProperty(name = "app.features.marketing.enabled", havingValue = "true")
public class SimulationTurnPlanner {

    static final int MIN_TURNS = 7;
    static final int MAX_TURNS = 12;
    static final int SOFT_CHAR_THRESHOLD = 800;

    /**
     * @param turnIdx           현재까지 완료된 턴 수 (0-based completed turns)
     * @param cumulativeUserChars 가상 사용자 A가 누적으로 입력한 총 글자 수
     * @param lastMediatorContent 마지막 중재자 응답 (없으면 null)
     * @return true이면 다음 턴 진행, false이면 종료
     */
    public boolean shouldContinue(int turnIdx, int cumulativeUserChars, String lastMediatorContent) {
        if (turnIdx < MIN_TURNS) return true;
        if (turnIdx >= MAX_TURNS) return false;
        // Soft stop: 누적 글자 수 초과
        if (cumulativeUserChars > SOFT_CHAR_THRESHOLD) return false;
        // Soft stop: 중재자가 마무리 신호를 보냈을 때
        if (lastMediatorContent != null && containsWrapUpSignal(lastMediatorContent)) return false;
        return true;
    }

    private boolean containsWrapUpSignal(String content) {
        return content.contains("정리") || content.contains("마무리") || content.contains("마치");
    }
}
