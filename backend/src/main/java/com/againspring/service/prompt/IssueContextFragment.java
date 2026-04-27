package com.againspring.service.prompt;

import com.againspring.domain.Session;
import org.springframework.stereotype.Component;

/**
 * Phase D — IssueContext를 프롬프트에 주입할 XML 블록으로 렌더.
 * PR-1: 항상 빈 문자열 반환 (회귀 0 보장). PR-3에서 실제 로직 추가.
 *
 * 권위본: shared/docs/policies/context-algorithm.md §4.2, §5.1(a)
 */
@Component
public class IssueContextFragment {

    public String render(Session session) {
        return ""; // PR-3에서 구현
    }
}
