package com.againspring.aiuser.llm.service;

import com.againspring.aiuser.llm.dto.CommentGenRequest;
import com.againspring.aiuser.llm.dto.PostGenRequest;
import com.againspring.aiuser.llm.dto.PostRewriteRequest;
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
        assertTrue(user.contains("첫 줄=제목"), "제목/본문 분리 출력 형식");
        assertTrue(user.contains("12~40자"), "제목 글자수 상한");
    }

    /** persona-diversity-v4 계약4 — personaCard가 있으면 "## 페르소나 특성"에 voiceProfile 대신 카드가 실린다. */
    @Test
    void postPromptPrefersPersonaCardOverVoiceProfileWhenPresent() {
        PostGenRequest withCard = PostGenRequest.builder()
            .personaId("p1").voiceProfile("v-원본-voiceProfile-문자열").slangLevel(0.3)
            .personaCard("[페르소나] 야근일상 · 34세 남 · 기혼 6년차")
            .category("WORK").archetype("work_credit_steal").formality("casual")
            .lengthTier("MEDIUM")
            .build();
        String prompt = assembler.assemblePostPrompt(withCard);
        String system = prompt.split("<<<USER_PROMPT>>>", 2)[0];

        assertTrue(system.contains("야근일상"), "personaCard 텍스트가 페르소나 특성에 실려야 함");
        assertFalse(system.contains("v-원본-voiceProfile-문자열"), "personaCard가 있으면 voiceProfile 문자열은 쓰지 않음");

        PostGenRequest withoutCard = PostGenRequest.builder()
            .personaId("p1").voiceProfile("v-원본-voiceProfile-문자열").slangLevel(0.3)
            .category("WORK").archetype("work_credit_steal").formality("casual")
            .lengthTier("MEDIUM")
            .build();
        String fallbackSystem = assembler.assemblePostPrompt(withoutCard).split("<<<USER_PROMPT>>>", 2)[0];
        assertTrue(fallbackSystem.contains("v-원본-voiceProfile-문자열"), "personaCard 없으면 기존 voiceProfile 문자열 유지");

        // 캐싱 불변식: personaCard 유무는 <<<PERSONA_SECTION>>> 앞 정적 prefix에 영향 없어야 함.
        String prefixWithCard = prompt.split(PERSONA_MARKER, 2)[0];
        String prefixWithoutCard = assembler.assemblePostPrompt(withoutCard).split(PERSONA_MARKER, 2)[0];
        assertEquals(prefixWithCard, prefixWithoutCard, "personaCard는 PERSONA_SECTION 뒤 가변 영역에만 영향을 줘야 함");
    }

    /**
     * 2026-09 순응도 개정 — dev 실측에서 축 지시(profanity=HEAVY 등)가 출력에 반영되지 않는 걸
     * 확인한 후, personaCard 블록을 <<<PERSONA_SECTION>>> 마커 직후 최상단(말투 규칙보다 먼저)에
     * 두고 "명령이지 배경정보가 아니다"라고 명시적으로 프레이밍했다. 이 테스트는 그 배치를 잠근다.
     */
    @Test
    void postPromptPlacesPersonaCardBeforeSpeechRulesForSalience() {
        String card = "[페르소나] 닉네임=야근일상 · 34세 남 · 기혼 6년차\n"
            + "[말투] 아래 문체 지시는 라벨이 아니라 명령이다 — 전부 실제 문장에 반영할 것:\n"
            + "- profanity=HEAVY: 욕설·비속어를 실제로 섞어 쓴다 — 순화하지 않는다";
        PostGenRequest req = PostGenRequest.builder()
            .personaId("p1").voiceProfile("v").slangLevel(0.3)
            .personaCard(card)
            .category("WORK").archetype("work_credit_steal").formality("casual")
            .lengthTier("MEDIUM")
            .build();
        String system = assembler.assemblePostPrompt(req).split("<<<USER_PROMPT>>>", 2)[0];

        int markerIdx = system.indexOf(PERSONA_MARKER);
        int personaHeaderIdx = system.indexOf("## 페르소나 특성");
        int speechHeaderIdx = system.indexOf("## 말투 규칙");
        int directiveIdx = system.indexOf("profanity=HEAVY");

        assertTrue(markerIdx >= 0 && personaHeaderIdx > markerIdx, "페르소나 특성 헤더는 마커 뒤에 있어야 함");
        assertTrue(personaHeaderIdx < speechHeaderIdx, "페르소나 특성이 말투 규칙보다 먼저 나와야 함(살리언스)");
        assertTrue(directiveIdx > personaHeaderIdx && directiveIdx < speechHeaderIdx,
            "축 지시 본문이 말투 규칙 이전, 페르소나 특성 헤더 이후에 와야 함");
        assertTrue(system.contains("배경 설명이 아니라 실행 명령이다"), "축 지시가 명령이라는 프레이밍 문구 포함");
    }

    /** persona-diversity-v4 계약4 — rewrite 경로도 personaCard가 있으면 voiceProfile 대신 카드를 쓴다. */
    @Test
    void rewritePromptPrefersPersonaCardOverVoiceProfileWhenPresent() {
        PostRewriteRequest withCard = PostRewriteRequest.builder()
            .personaId("p1").voiceProfile("v-원본-voiceProfile-문자열").slangLevel(0.3)
            .personaCard("[페르소나] 야근일상 · 34세 남 · 기혼 6년차")
            .category("WORK").targetCategory("WORK").formality("casual")
            .originalTitle("제목").originalBody("본문")
            .build();
        String system = assembler.assemblePostRewritePrompt(withCard).split("<<<USER_PROMPT>>>", 2)[0];

        assertTrue(system.contains("야근일상"), "personaCard 텍스트가 페르소나 특성에 실려야 함");
        assertFalse(system.contains("v-원본-voiceProfile-문자열"), "personaCard가 있으면 voiceProfile 문자열은 쓰지 않음");

        PostRewriteRequest withoutCard = PostRewriteRequest.builder()
            .personaId("p1").voiceProfile("v-원본-voiceProfile-문자열").slangLevel(0.3)
            .category("WORK").targetCategory("WORK").formality("casual")
            .originalTitle("제목").originalBody("본문")
            .build();
        String fallbackSystem = assembler.assemblePostRewritePrompt(withoutCard).split("<<<USER_PROMPT>>>", 2)[0];
        assertTrue(fallbackSystem.contains("v-원본-voiceProfile-문자열"), "personaCard 없으면 기존 voiceProfile 문자열 유지");
    }

    @Test
    void authorPairedPromptLeavesCounterpartAnchors() {
        PostGenRequest req = PostGenRequest.builder()
            .personaId("p1").voiceProfile("v").slangLevel(0.3)
            .category("COUPLE").archetype("couple_communication").formality("casual")
            .lengthTier("MEDIUM")
            .stance("AUTHOR")
            .build();
        String prompt = assembler.assemblePostPrompt(req);
        String user = prompt.split("<<<USER_PROMPT>>>", 2)[1];

        assertTrue(user.contains("양면 사연의 작성자"), "AUTHOR stance → 양면 작성자 프롬프트");
        assertTrue(user.contains("상대 속마음") || user.contains("단정하지"), "상대 의도 단정 금지");
        assertTrue(user.contains("앵커") || user.contains("재해석"), "상대가 받을 사건 앵커 지시");
        assertFalse(user.contains("[작성자가 쓴 원글]"), "AUTHOR는 파트너 프롬프트가 아님");
    }

    @Test
    void partnerPromptDemandsPeerWeightBody() {
        PostGenRequest req = PostGenRequest.builder()
            .personaId("p2").voiceProfile("v").slangLevel(0.3)
            .category("COUPLE").archetype("couple_communication").formality("casual")
            .lengthTier("MEDIUM")
            .stance("PARTNER")
            .counterpartBody("어제 남친이 또 늦게 와서 밥도 같이 못 먹었어")
            .build();
        String prompt = assembler.assemblePostPrompt(req);
        String user = prompt.split("<<<USER_PROMPT>>>", 2)[1];

        assertTrue(user.contains("[작성자가 쓴 원글]"));
        assertTrue(user.contains("상대방(B)") || user.contains("같은 무게"), "동등 분량 지시");
        assertTrue(user.contains("제목 줄 없이") || user.contains("본문만"), "파트너는 본문만");
        assertTrue(user.contains("새 사건 추가 금지") || user.contains("재참조"));
    }

    @Test
    void rewritePromptDemandsJsonAndTargetCategory() {
        PostRewriteRequest req = PostRewriteRequest.builder()
            .personaId("p1")
            .voiceProfile("v")
            .slangLevel(0.4)
            .formality("casual")
            .category("WORK")
            .targetCategory("OTHER")
            .originalTitle("보고서 때문에 너무 짜증남")
            .originalBody("팀장이 또 내 보고서를 자기 이름으로 올렸는데 어제도 비슷한 일이 있었음")
            .rewriteInstruction("직장 광장 티가 너무 강하면 기타 광장처럼 완화")
            .build();

        String prompt = assembler.assemblePostRewritePrompt(req);
        String user = prompt.split("<<<USER_PROMPT>>>", 2)[1];

        assertTrue(user.contains("[현재 제목]"));
        assertTrue(user.contains("최종 광장: OTHER"));
        assertTrue(user.contains("결과는 JSON 1개만 출력"));
        assertTrue(user.contains("\"title\":\"...\""));
        assertTrue(user.contains("새 글로 갈아엎지 말고"), "부분 교정 규칙 유지");
        assertTrue(user.contains("12~40자"), "제목 글자수");
        assertTrue(user.contains("제목=본문 동일 문자열 금지"));
    }

    // ── 2026-08-16 shortform-content-quality fix: 오타 재현 지시는 댓글/대댓글에만 남는다 ──

    private static final String TYPO_INSTRUCTION_MARKER = "consistent_errors가 있으면";

    @Test
    void postPromptExcludesTypoInstruction() {
        PostGenRequest req = PostGenRequest.builder()
            .personaId("p1").voiceProfile("v").slangLevel(0.3)
            .category("WORK").archetype("work_credit_steal").formality("casual")
            .lengthTier("MEDIUM")
            .build();
        String prompt = assembler.assemblePostPrompt(req);

        assertFalse(prompt.contains(TYPO_INSTRUCTION_MARKER),
            "공개 사연(글)은 의도적 오타 재현 지시를 포함하면 안 됨 — 오타는 게시 전 교정 단계로만 걸러짐");
        assertTrue(prompt.contains("의도적인 오탈자는 넣지 않음"), "글은 오타 비주입 지시로 대체됨");
    }

    @Test
    void postRewritePromptExcludesTypoInstruction() {
        PostRewriteRequest req = PostRewriteRequest.builder()
            .personaId("p1").voiceProfile("v").slangLevel(0.3).formality("casual")
            .category("WORK").targetCategory("WORK")
            .originalTitle("제목").originalBody("본문")
            .build();
        String prompt = assembler.assemblePostRewritePrompt(req);

        assertFalse(prompt.contains(TYPO_INSTRUCTION_MARKER), "글 재작성도 오타 재현 지시 제외");
    }

    @Test
    void commentPromptStillIncludesTypoInstruction() {
        String prompt = assembler.assembleCommentPrompt(commentReq());

        assertTrue(prompt.contains(TYPO_INSTRUCTION_MARKER), "댓글은 기존대로 오타 재현 지시 유지");
    }

    @Test
    void replyPromptStillIncludesTypoInstruction() {
        ReplyGenRequest req = ReplyGenRequest.builder()
            .personaId("p1").voiceProfile("v").slangLevel(0.3)
            .parentCommentExcerpt("나도 그랬음").stance("AGREE").formality("casual")
            .build();
        String prompt = assembler.assembleReplyPrompt(req);

        assertTrue(prompt.contains(TYPO_INSTRUCTION_MARKER), "대댓글은 기존대로 오타 재현 지시 유지");
    }
}
