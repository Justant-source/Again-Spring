package com.againspring.service.context;

import com.againspring.domain.Session;
import org.springframework.stereotype.Component;
import java.util.Set;

/**
 * Phase D — facts/needs에 RatioElement를 태깅.
 * LLM이 contributesTo를 명시한 경우 그대로 사용, 없으면 텍스트 기반 휴리스틱으로 매핑.
 *
 * 권위본: shared/docs/policies/context-algorithm.md §2.2, ratio-calculation.md §5요소
 */
@Component
public class RatioElementTagger {

    private static final Set<String> BOUNDARY_KEYWORDS = Set.of("약속", "거짓말", "숨겼", "배신", "신뢰", "믿음을");
    private static final Set<String> REPAIR_KEYWORDS = Set.of("사과", "미안", "화해", "받아주", "이해해");

    public Session.RatioElement tagFact(Session.IssueFact fact) {
        if (fact.contributesTo != null) return fact.contributesTo;
        String text = fact.text == null ? "" : fact.text;
        for (String kw : BOUNDARY_KEYWORDS) {
            if (text.contains(kw)) return Session.RatioElement.BOUNDARY;
        }
        for (String kw : REPAIR_KEYWORDS) {
            if (text.contains(kw)) return Session.RatioElement.REPAIR;
        }
        return null; // 분류 불가 — ratio 계산 시 무시
    }

    public Session.RatioElement tagNeed(Session.NeedSlot need) {
        if (need.contributesTo != null) return need.contributesTo;
        return Session.RatioElement.PERSPECTIVE; // 욕구는 대개 perspective 보강
    }
}
