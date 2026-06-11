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
    void disabledServiceAlwaysPasses() {
        ReflectionTestUtils.setField(service, "enabled", false);
        assertTrue(service.quickCheck("정말 공감되네요 힘내세요 응원합니다", "comment", "polite").passed());
    }
}
