package com.againspring.aiuser.llm.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 자기비평 결정론 체크 테스트 — AI투 상투구·강조어·ㅠ 남발 (문체 현실화 S4).
 */
class SelfCritiqueServiceTest {

    private SelfCritiqueService service;

    @BeforeEach
    void setUp() {
        service = new SelfCritiqueService(null, null, null);
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "passThreshold", 5);
    }

    @Test
    void flagsAiCliches() {
        SelfCritiqueService.CritiqueResult r =
            service.quickCheck("그 상황 공감되네요 힘내세요", "comment", "polite");
        assertTrue(r.issues().stream().anyMatch(i -> i.contains("AI 상투구")));

        SelfCritiqueService.CritiqueResult r2 =
            service.quickCheck("충분히 화낼 수 있어요 응원합니다", "comment", "polite");
        assertTrue(r2.issues().stream().anyMatch(i -> i.contains("AI 상투구")));
    }

    @Test
    void flagsEmphasisOveruse() {
        // 진짜+정말 합산 3회 이상 → 감점
        SelfCritiqueService.CritiqueResult r =
            service.quickCheck("진짜 어이없고 진짜 황당한데 정말 모르겠음", "comment", "casual");
        assertTrue(r.issues().stream().anyMatch(i -> i.contains("강조어 남발")));

        // 2회까지는 허용
        SelfCritiqueService.CritiqueResult ok =
            service.quickCheck("진짜 어이없네 그건 정말 선 넘었음", "comment", "casual");
        assertFalse(ok.issues().stream().anyMatch(i -> i.contains("강조어 남발")));
    }

    @Test
    void flagsSobOveruse() {
        SelfCritiqueService.CritiqueResult r =
            service.quickCheck("어휴 ㅠㅠ 나도 그랬어 ㅠ 너무하다 ㅠㅠ", "comment", "casual");
        assertTrue(r.issues().stream().anyMatch(i -> i.contains("ㅠ 남발")));

        SelfCritiqueService.CritiqueResult ok =
            service.quickCheck("어휴 나도 그랬어 너무하네 ㅠㅠ", "comment", "casual");
        assertFalse(ok.issues().stream().anyMatch(i -> i.contains("ㅠ 남발")));
    }

    @Test
    void cleanCommunityTextPasses() {
        SelfCritiqueService.CritiqueResult r = service.quickCheck(
            "어제 회사에서 팀장이 또 보고서 가로챘다며 그건 메일로 기록 남겨놔 이직할 때도 씀",
            "comment", "casual");
        assertTrue(r.passed(), "정상 커뮤니티 문체는 통과해야 함: " + r.issues());
    }

    @Test
    void extraClichesPropertyExtendsDetection() {
        ReflectionTestUtils.setField(service, "extraCliches", "함께 고민해봐요, 좋은 하루 되세요");
        SelfCritiqueService.CritiqueResult r =
            service.quickCheck("우리 함께 고민해봐요", "comment", "polite");
        assertTrue(r.issues().stream().anyMatch(i -> i.contains("AI 상투구")));
    }

    @Test
    void retryPromptOmitsOriginalGenerationBlobAndKeepsFullDraft() {
        String draft = "다들 어떻게 생각해요? 저만 이상한가요 진짜";
        String prompt = service.buildRetryPrompt(
                draft,
                java.util.List.of("반말 위반(~요/~어요 사용) — ~음/~임/~더라 류 반말로 고쳐라"),
                "post",
                "casual");
        assertFalse(prompt.contains("<<<USER_PROMPT>>>"));
        assertFalse(prompt.contains("원래 요청"));
        assertTrue(prompt.contains(draft));
        assertTrue(prompt.contains("반말"));
        assertTrue(prompt.contains("[원문]"));
    }

    @Test
    void retryPromptIncludesStructuralTellChecklist() {
        String prompt = service.buildRetryPrompt(
                "다들 어떻게 생각해요? 저만 이상한가요 진짜",
                java.util.List.of("반말 위반(~요/~어요 사용) — ~음/~임/~더라 류 반말로 고쳐라"),
                "post",
                "casual");
        assertTrue(prompt.contains("마지막 문단"));
        assertTrue(prompt.contains("아니라"));
    }

    @Test
    void disabledServiceAlwaysPasses() {
        ReflectionTestUtils.setField(service, "enabled", false);
        assertTrue(service.quickCheck("정말 공감되네요 힘내세요 응원합니다", "comment", "polite").passed());
    }

    @Test
    void detectsExcessiveCommaRate() {
        // 쉼표 10개 / 100자 = 10% > 5% 임계 → 감지
        String highComma = "어제,학교,갔는데,친구,만나서,같이,밥,먹었음,진짜,좋았음 그랬는데 이상하게 됐어";
        SelfCritiqueService.CritiqueResult result = service.quickCheck(highComma, "comment", "casual");
        assertTrue(result.issues().stream().anyMatch(i -> i.contains("쉼표 과다")),
            "쉼표 과다(10%) 감지 실패");

        // 쉼표 1개 / 100자 = 1% < 5% → 통과
        String lowComma = "어제 학교 갔는데 친구 만나서 같이 밥 먹었음 진짜 좋았음 그랬는데 이상하게 됐어";
        SelfCritiqueService.CritiqueResult clean = service.quickCheck(lowComma, "comment", "casual");
        assertTrue(clean.issues().stream().noneMatch(i -> i.contains("쉼표 과다")),
            "정상 쉼표율 오감지");
    }

    @Test
    void detectsClosingSummaryParagraphCandidate() {
        // 마지막 문단이 요약 표지로 시작 — 코퍼스에 실증 사례는 없어 가설 단계, 로그 전용
        String withSummary = "어제 회사에서 있었던 일임\n\n정리하자면, 팀장이 문제였다는 거임";
        assertTrue(SelfCritiqueService.hasClosingSummaryParagraph(withSummary));

        // "결국"이 문단 중간 인과 접속사로만 쓰이는 실제 코퍼스 패턴 — 오탐 아니어야 함
        String midNarrative = "어제 회사에서 있었던 일임\n\n결국 내가 다시 만들어서 제출했는데 팀장이 동료를 칭찬했음";
        assertFalse(SelfCritiqueService.hasClosingSummaryParagraph(midNarrative));
    }

    @Test
    void countsSymmetricContrastOccurrences() {
        // blind_kit_v1_20260621125413.md:129, :189 실제 문장 기반
        String twice = "내가 원하는 건 신뢰가 아니라 그냥 인정임. 한두 마디가 아니라 계속 욕을 했어";
        assertEquals(2, SelfCritiqueService.countSymmetricContrast(twice));

        String once = "내가 원하는 건 신뢰가 아니라 그냥 인정임";
        assertEquals(1, SelfCritiqueService.countSymmetricContrast(once));
    }

    @Test
    void structuralTellCandidatesDoNotAffectScore() {
        // 로그만 남기고 score/passed/issues는 절대 안 바뀌어야 함
        String text = "내가 원하는 건 신뢰가 아니라 그냥 인정임\n\n정리하자면, 그게 다임";
        SelfCritiqueService.CritiqueResult withoutOtherIssues = service.quickCheck(text, "comment", "casual");
        assertTrue(withoutOtherIssues.passed(), "구조적 후보만으로는 감점되면 안 됨: " + withoutOtherIssues.issues());
        assertEquals(7, withoutOtherIssues.score());
    }
}
