package com.againspring.service.prompt;

import static org.junit.jupiter.api.Assertions.*;

import com.againspring.domain.Session;
import com.againspring.domain.enums.MessageSender;
import org.junit.jupiter.api.Test;

/**
 * PR-1 골격 검증 — 빈 컨텍스트에서 fragment가 빈 문자열을 반환하는지 확인.
 * PR-2/3/4에서 실제 로직이 추가되면 이 테스트는 그대로 유지 (빈 세션 케이스는 항상 빈 반환).
 */
class PhaseDFragmentSkeletonTest {

    @Test
    void issueContextFragment_returnsEmptyString_whenSessionHasNoContext() {
        Session session = new Session();
        assertEquals("", new IssueContextFragment().render(session));
    }

    @Test
    void issueContextFragment_returnsEmptyString_whenSessionNull() {
        assertEquals("", new IssueContextFragment().render(null));
    }

    @Test
    void userStateFragment_returnsEmptyString_whenSessionHasNoHistory_soloMode() {
        Session session = new Session();
        assertEquals("", new UserStateFragment().render(session, false));
    }

    @Test
    void userStateFragment_returnsEmptyString_whenSessionHasNoHistory_duoMode() {
        Session session = new Session();
        assertEquals("", new UserStateFragment().render(session, true));
    }

    @Test
    void questionQueueFragment_returnsEmptyString_whenQueueNull_userA() {
        Session session = new Session();
        assertEquals("", new QuestionQueueFragment().render(session, MessageSender.USER_A));
    }

    @Test
    void questionQueueFragment_returnsEmptyString_whenQueueNull_userB() {
        Session session = new Session();
        assertEquals("", new QuestionQueueFragment().render(session, MessageSender.USER_B));
    }
}
