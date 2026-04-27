package com.againspring.service.prompt;

import com.againspring.domain.Session;
import com.againspring.domain.enums.MessageSender;
import org.springframework.stereotype.Component;

/**
 * Phase D — 현재 사용자의 PQ 상위 N개를 프롬프트에 주입.
 * PR-1: 항상 빈 문자열 반환 (회귀 0 보장). PR-4에서 실제 로직 추가.
 *
 * 권위본: shared/docs/policies/context-algorithm.md §4.4, §5.1(c)
 */
@Component
public class QuestionQueueFragment {

    public String render(Session session, MessageSender currentUserSender) {
        return ""; // PR-4에서 구현
    }
}
