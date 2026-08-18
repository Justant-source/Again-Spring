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
}
