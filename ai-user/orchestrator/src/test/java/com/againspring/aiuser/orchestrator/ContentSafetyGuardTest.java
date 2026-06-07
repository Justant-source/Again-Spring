package com.againspring.aiuser.orchestrator;

import com.againspring.aiuser.orchestrator.safety.ContentSafetyGuard;
import com.againspring.aiuser.orchestrator.safety.ContentSafetyGuard.ContentType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 2026-06-07 인시던트 회귀 방지: 토큰/크레딧 소진 시 제공자 오류 문자열이
 * 글·댓글로 게시되면 안 된다. ContentSafetyGuard가 최종 차단해야 한다.
 */
class ContentSafetyGuardTest {

    private final ContentSafetyGuard guard = new ContentSafetyGuard();

    @Test
    void blocksProviderErrorText() {
        // 실제 인시던트 문자열
        assertFalse(guard.check("Credit balance is too low", ContentType.COMMENT).passed());
        assertFalse(guard.check("Credit balance is too low", ContentType.POST).passed());
        // 기타 제공자 오류 시그니처
        assertFalse(guard.check("Your credit balance is too low to access the Anthropic API", ContentType.POST).passed());
        assertFalse(guard.check("rate limit exceeded, try again", ContentType.COMMENT).passed());
        assertFalse(guard.check("overloaded_error", ContentType.COMMENT).passed());

        assertEquals("LLM_ERROR_SIGNATURE",
            guard.check("Credit balance is too low", ContentType.COMMENT).reason());
    }

    @Test
    void allowsNormalKoreanContent() {
        assertTrue(guard.check("어제 남친이 내 말 끊었어 진짜 답답해 ㅠㅠ", ContentType.COMMENT).passed());
        assertTrue(guard.check("벌써 세 번째야 내가 뭘 잘못한 건지 모르겠어", ContentType.POST).passed());
    }
}
