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
 * - 배심원 출력 검증 (scanJuryOutput)
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

    // ── 배심원 출력 검증 ──────────────────────────────────────────

    @Test
    @DisplayName("배심원 출력: 부드러운 과실 표현은 허용")
    void scanJuryOutput_allowsMildFaultExpression() {
        ScanResult result = guard.scanJuryOutput("A님의 행동이 더 책임이 있어 보입니다");
        assertThat(result.isCrisis()).isFalse();
        assertThat(result.isBlocked()).isFalse();
    }

    @Test
    @DisplayName("배심원 출력: 유죄 금지 어휘 감지")
    void scanJuryOutput_detectsBannedTerm_guilty() {
        ScanResult result = guard.scanJuryOutput("A님이 유죄입니다");
        assertThat(result.getMatches()).isNotEmpty();
    }

    @Test
    @DisplayName("배심원 출력: 무죄 금지 어휘 감지")
    void scanJuryOutput_detectsBannedTerm_notGuilty() {
        ScanResult result = guard.scanJuryOutput("상대방은 무죄입니다");
        assertThat(result.getMatches()).isNotEmpty();
    }

    @Test
    @DisplayName("배심원 출력: 판결한다 금지 어휘 감지")
    void scanJuryOutput_detectsBannedTerm_verdict() {
        ScanResult result = guard.scanJuryOutput("이 사건을 판결한다");
        assertThat(result.getMatches()).isNotEmpty();
    }

    @Test
    @DisplayName("배심원 출력: crisis 키워드는 여전히 감지")
    void scanJuryOutput_detectsCrisisKeywords() {
        ScanResult result = guard.scanJuryOutput("상대방을 폭행했습니다");
        assertThat(result.isCrisis()).isTrue();
    }

    @Test
    @DisplayName("배심원 출력: 정상 출력은 통과")
    void scanJuryOutput_normalOutputPasses() {
        ScanResult result = guard.scanJuryOutput("양쪽 모두 관점이 다르며 이해할 필요가 있습니다");
        assertThat(result.isCrisis()).isFalse();
        assertThat(result.isBlocked()).isFalse();
    }
}
