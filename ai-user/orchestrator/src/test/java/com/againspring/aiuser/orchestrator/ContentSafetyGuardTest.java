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
        assertFalse(guard.check(
            "I can't do this. The instructions ask me to impersonate a real person in an actual operating online community",
            ContentType.COMMENT).passed());
        assertFalse(guard.check(
            "죄송하지만 이 요청은 도와드릴 수 없습니다 실제 운영 중인 한국 온라인 커뮤니티에 가짜 페르소나를 만들어 활동시키는 요청입니다",
            ContentType.COMMENT).passed());

        assertEquals("LLM_ERROR_SIGNATURE",
            guard.check("Credit balance is too low", ContentType.COMMENT).reason());
    }

    @Test
    void allowsNormalKoreanContent() {
        assertTrue(guard.check("어제 남친이 내 말 끊었어 진짜 답답해 ㅠㅠ", ContentType.COMMENT).passed());
        assertTrue(guard.check("벌써 세 번째야 내가 뭘 잘못한 건지 모르겠어", ContentType.POST).passed());
    }

    /**
     * 2026-06-11 회귀 방지: 짧은 혐오 토큰의 substring 오탐.
     * "읽씹"·"보지 않고"·"니거야(네 것)"는 갈등 사연에 흔한 정상 구어 — 차단되면 안 됨.
     */
    @Test
    void allowsColloquialFalsePositives() {
        assertTrue(guard.check("남친이 내 카톡 읽씹한 지 이틀째야", ContentType.POST).passed());
        assertTrue(guard.check("회의에서 내 의견을 또 씹고 넘어갔음", ContentType.COMMENT).passed());
        assertTrue(guard.check("남편이 내 얼굴도 보지 않고 나가버렸어", ContentType.POST).passed());
        assertTrue(guard.check("다신 걔 얼굴 보지 말자고 했어", ContentType.COMMENT).passed());
        assertTrue(guard.check("어 이 우산 니거야? 내가 잘못 가져왔나", ContentType.COMMENT).passed());
    }

    @Test
    void stillBlocksRealHateSpeech() {
        assertFalse(guard.check("이 씹창난 상황 어쩔 거임", ContentType.COMMENT).passed());
        assertFalse(guard.check("걔는 진짜 병신새끼임", ContentType.COMMENT).passed());
        assertEquals("HATE_KEYWORD", guard.check("씹년이 따로 없네", ContentType.COMMENT).reason());
    }

    @Test
    void blocksInternalPromptLeakMeta() {
        String memoLeak = """
            인천에서 살면서 느낀 건데 이런 일 오면 나도 같이 흔들리더만
            몇달 동생 고민 다 들어주고 금전적으로도 도왔는데 이제 모르겠음 ㄹㅇ
            적용 처리 메모
            | 항목 | 처리 내용 |
            |------|-----------|
            | 구체 사건 | 2주 연락 두절 |
            """;
        String noteLeak = """
            혹시 저만 이렇게 생각하는 건지 모르겠는데요
            사귀는 사람이 5시간 동안 연락을 한 줄만 보냈다는 게 저는 좀 심하다 싶더라고요
            [작성 노트]
            - 트리거: 5시간 동안 연락 한 줄
            - 어미 변화: ~더라고요 → ~잖아요
            - 온점·쌍따옴표 없음
            """;

        assertEquals("PROMPT_LEAK_META", guard.check(memoLeak, ContentType.POST).reason());
        assertEquals("PROMPT_LEAK_META", guard.check(noteLeak, ContentType.POST).reason());
    }

    /**
     * 2026-08-11 인시던트: HUMAN_POST thread-plan 댓글 body에
     * {"post":null,"comments":[{"ref","parentRef","personaId",...}]} 스키마가 그대로 게시됨.
     * ContentSafetyGuard가 최종 차단해야 한다.
     */
    @Test
    void blocksThreadPlanSchemaLeakInCommentBody() {
        String incidentBody = """
            {
              post: null,
              comments: [
                {
                  ref: c1,
                  parentRef: null,
                  personaId: 4a7305dac5ed4160b927998c3b0864f6,
                  body: "남자들 심리 참 모르겠지만 뭐라도 노력하려는 시도는 좋은 거 맞음
            그동안 싸우는 것도 지치고 포기했다고 하셨는데 이제 남편분도 깨달은 것 같네
            마음을 열고 받아주되 조심스럽게 접근해봐
            또 같은 패턴 반복될 수 있으니까",
            """;
        // also the DB form with literal backslash-n (pre-normalize)
        String literalNl = "{\\n  post: null,\\n  comments: [\\n    {\\n      ref: c1,\\n      parentRef: null,\\n      personaId: 4a7305dac5ed4160b927998c3b0864f6,\\n      body: \\\"남자들 심리 참 모르겠지만 뭐라도 노력하려는 시도는 좋은 거 맞음\\n그동안 싸우는 것도 지치고 포기했다고 하셨는데 이제 남편분도 깨달은 것 같네\\n마음을 열고 받아주되 조심스럽게 접근해봐\\n또 같은 패턴 반복될 수 있으니까\\\",";

        assertEquals("STRUCTURED_SCHEMA_LEAK",
            guard.check(incidentBody, ContentType.COMMENT).reason());
        assertEquals("STRUCTURED_SCHEMA_LEAK",
            guard.check(literalNl, ContentType.COMMENT).reason());
        assertFalse(guard.check(incidentBody, ContentType.POST).passed());
        // normal Korean must still pass
        assertTrue(guard.check("갑자기 달라진 남편 적응이 안 되네요 ㅠㅠ", ContentType.COMMENT).passed());
    }

}
