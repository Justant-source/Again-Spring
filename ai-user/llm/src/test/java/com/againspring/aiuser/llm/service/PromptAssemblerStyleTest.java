package com.againspring.aiuser.llm.service;

import com.againspring.aiuser.llm.dto.CommentGenRequest;
import com.againspring.aiuser.llm.dto.PostGenRequest;
import com.againspring.aiuser.llm.dto.ReplyGenRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 문체 현실화 프롬프트 블록 테스트 (S1·S2·S3).
 * 핵심 불변: <<<PERSONA_SECTION>>> 앞 정적 prefix는 요청 내용과 무관하게 동일해야 함
 * (ClaudeApiInvoker 프롬프트 캐싱 경계 — 깨지면 캐시 미스로 비용 급증).
 */
class PromptAssemblerStyleTest {

    private static final String PERSONA_MARKER = "<<<PERSONA_SECTION>>>";
    private PromptAssembler assembler;

    @BeforeEach
    void setUp() {
        assembler = new PromptAssembler();
        assembler.reload(); // jdbcTemplate 없음 → classpath voice/*.md 폴백
    }

    private CommentGenRequest commentReq() {
        return CommentGenRequest.builder()
            .personaId("p1").voiceProfile("따뜻한 40대 주부").slangLevel(0.3)
            .postTitle("남편이 또 약속을 어겼어").postBodyExcerpt("어제 남편이 늦게 와서...")
            .stance("AUTHOR").formality("casual")
            .build();
    }

    @Test
    void cachePrefixIsInvariantAcrossRequests() {
        CommentGenRequest a = commentReq();
        a.setRecentOutputs("- 진짜 너무하네 ㅠ\n- 나도 그랬음");
        a.setStyleExamples("어휴 그건 좀 아니다\n---\n기록 남겨놔");
        a.setModeHint("반응만: 감정 한 줄만, 10~30자");

        CommentGenRequest b = commentReq();
        b.setPersonaId("p2");
        b.setVoiceProfile("거친 20대 게이머");
        b.setPostTitle("완전 다른 제목");

        String prefixA = assembler.assembleCommentPrompt(a).split(PERSONA_MARKER, 2)[0];
        String prefixB = assembler.assembleCommentPrompt(b).split(PERSONA_MARKER, 2)[0];
        assertEquals(prefixA, prefixB, "캐시 prefix가 요청 간 동일해야 함 (프롬프트 캐싱 보호)");
    }

    @Test
    void commentPromptRendersStyleBlocks() {
        CommentGenRequest req = commentReq();
        req.setRecentOutputs("- 진짜 너무하네 ㅠ");
        req.setStyleExamples("어휴 그건 좀 아니다");
        req.setModeHint("되묻기: 궁금한 점 하나만, 15~40자");

        String prompt = assembler.assembleCommentPrompt(req);
        String user = prompt.split("<<<USER_PROMPT>>>", 2)[1];

        assertTrue(user.contains("[내가 최근에 쓴 댓글 — 반복 방지]"), "히스토리 블록");
        assertTrue(user.contains("진짜 너무하네 ㅠ"));
        assertTrue(user.contains("[참고 문체 샘플"), "스타일 few-shot 블록");
        assertTrue(user.contains("되묻기: 궁금한 점 하나만"), "모드 힌트");
        assertTrue(prompt.contains("synthetic=1"), "실사용자 사칭 금지 경계 포함");
        assertFalse(user.contains("50~150자 내외"), "모드 힌트가 고정 길이 지시를 대체");
    }

    @Test
    void commentPromptFallsBackWithoutOptionalBlocks() {
        String prompt = assembler.assembleCommentPrompt(commentReq());
        String user = prompt.split("<<<USER_PROMPT>>>", 2)[1];
        assertFalse(user.contains("[내가 최근에 쓴"), "히스토리 없으면 블록 생략");
        assertFalse(user.contains("[참고 문체 샘플"));
        // 2026-06-16 초단문화: 기본 fallback이 "초단문 필수: 10~35자"로 변경됨 (50~150자 → 폐기)
        assertTrue(user.contains("초단문 필수"), "모드 힌트 없으면 초단문 길이 지시");
    }

    @Test
    void replyPromptDropsClicheEncouragement() {
        ReplyGenRequest req = ReplyGenRequest.builder()
            .personaId("p1").voiceProfile("v").slangLevel(0.3)
            .parentCommentExcerpt("나도 그랬음").stance("AGREE").formality("casual")
            .build();
        String prompt = assembler.assembleReplyPrompt(req);
        String user = prompt.split("<<<USER_PROMPT>>>", 2)[1];

        assertFalse(user.contains("💚"), "💚 권장 제거됨");
        assertFalse(user.contains("감정 강조 자연스러움"), "정말/진짜 반복 권장 제거됨");
        assertTrue(user.contains("강조어"), "강조어 변주 지시 존재");
    }

    @Test
    void postPromptRendersRecentOutputsWithTopicBan() {
        PostGenRequest req = PostGenRequest.builder()
            .personaId("p1").voiceProfile("v").slangLevel(0.3)
            .category("WORK").archetype("work_credit_steal").formality("casual")
            .lengthTier("MEDIUM")
            .recentOutputs("- 팀장이 보고서 가로챘음")
            .build();
        String prompt = assembler.assemblePostPrompt(req);
        String user = prompt.split("<<<USER_PROMPT>>>", 2)[1];

        assertTrue(user.contains("[내가 최근에 쓴 글 — 반복 방지]"));
        // Phase 6에서 extraRule 문구 변경: "같은 소재·사건 유형 반복 금지" → "완전히 다른 유형의 갈등 상황으로 쓸 것"
        // PromptAssembler.java:180 기준값
        assertTrue(user.contains("완전히 다른 유형의 갈등 상황으로 쓸 것"), "글은 소재 반복 금지 규칙 포함");
    }
}
