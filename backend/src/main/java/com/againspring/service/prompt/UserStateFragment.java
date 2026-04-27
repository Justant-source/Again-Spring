package com.againspring.service.prompt;

import com.againspring.domain.Session;
import org.springframework.stereotype.Component;

/**
 * Phase D — 가장 최근 UserStateEntry를 프롬프트에 주입.
 * PR-1: 항상 빈 문자열 반환 (회귀 0 보장). PR-2에서 실제 로직 추가.
 *
 * 권위본: shared/docs/policies/context-algorithm.md §4.3, §5.1(b)
 */
@Component
public class UserStateFragment {

    public String render(Session session, boolean isDuo) {
        return ""; // PR-2에서 구현
    }
}
