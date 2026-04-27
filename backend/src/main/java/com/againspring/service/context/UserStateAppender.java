package com.againspring.service.context;

import com.againspring.domain.Session;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;

/**
 * Phase D — LLM이 분류한 UserState를 Session.userStateHistory에 누적.
 * 권위본: shared/docs/policies/context-algorithm.md §4.3
 */
@Component
public class UserStateAppender {

    public void append(Session session, Session.UserStateEntry entry) {
        if (entry == null || entry.state == null) return;
        List<Session.UserStateEntry> hist = session.getUserStateHistory();
        if (hist == null) hist = new ArrayList<>();
        hist.add(entry);
        session.setUserStateHistory(hist);
    }
}
