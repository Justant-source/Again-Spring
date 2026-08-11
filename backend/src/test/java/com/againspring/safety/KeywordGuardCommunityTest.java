package com.againspring.safety;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * V17 커뮤니티 기능 KeywordGuard 확장 테스트.
 * - 공개 reframe 치환 (applyCommunityPublicReframe)
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {KeywordGuard.class})
@DisplayName("V17 KeywordGuard Community Tests")
class KeywordGuardCommunityTest {

    @Autowired
    private KeywordGuard guard;

    @BeforeEach
    void setUp() {
        assertThat(guard).isNotNull();
    }

    // ── 공개 reframe ──────────────────────────────────────────────

    @Test
    @DisplayName("공개 reframe: 과실비율→공감 분포 치환")
    void applyCommunityPublicReframe_replacesLegalTerms() {
        String result = guard.applyCommunityPublicReframe("A님의 과실비율이 높습니다");
        assertThat(result).doesNotContain("과실비율");
        assertThat(result).contains("공감 분포");
    }

    @Test
    @DisplayName("공개 reframe: 가해자→A님 치환")
    void applyCommunityPublicReframe_replacesAbuser() {
        String result = guard.applyCommunityPublicReframe("가해자 측 행동은 문제가 있습니다");
        assertThat(result).doesNotContain("가해자");
    }

    @Test
    @DisplayName("공개 reframe: 피해자→B님 치환")
    void applyCommunityPublicReframe_replacesVictim() {
        String result = guard.applyCommunityPublicReframe("피해자는 상처가 있습니다");
        assertThat(result).doesNotContain("피해자");
        assertThat(result).contains("B님");
    }

    @Test
    @DisplayName("공개 reframe: 판결→결과 치환")
    void applyCommunityPublicReframe_replacesJudgment() {
        String result = guard.applyCommunityPublicReframe("판결을 존중해야 합니다");
        assertThat(result).doesNotContain("판결");
        assertThat(result).contains("결과");
    }

    @Test
    @DisplayName("공개 reframe: null 입력은 그대로 반환")
    void applyCommunityPublicReframe_handlesNull() {
        String result = guard.applyCommunityPublicReframe(null);
        assertThat(result).isNull();
    }
}
