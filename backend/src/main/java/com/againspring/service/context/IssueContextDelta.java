package com.againspring.service.context;

import com.againspring.domain.Session;
import java.util.List;

/**
 * Phase D — LLM이 <turn_meta>.issue_delta 로 보낸 이슈 컨텍스트 변경분.
 * 권위본: shared/docs/policies/context-algorithm.md §5.2
 */
public class IssueContextDelta {
    public String headline;
    public List<Session.IssueFact> factsAdded;
    public List<String> factsConfirmed;
    public List<Session.NeedSlot> needsAdded;
    public List<Session.UnresolvedThread> threadsAdded;
    public List<String> threadsResolved;
}
