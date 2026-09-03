package com.againspring.safety;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.assertThat;

class CrisisKeywordGuardTest {
    private CrisisKeywordGuard guard;

    @BeforeEach
    void setUp() {
        guard = new CrisisKeywordGuard();
        ReflectionTestUtils.setField(guard, "configPath", "classpath:/safety/crisis-keywords.yml");
        guard.loadKeywords();
    }

    @Test
    void detectsSelfHarmAndViolence() {
        assertThat(guard.scan("요즘 진짜 죽고싶다").crisis()).isTrue();
        assertThat(guard.scan("남편이 애를 때렸어요").patterns()).contains("때렸");
    }

    @Test
    void legalAndJudgementWordsAreNotCrisis() {
        CrisisScanResult r = guard.scan("판결도 났고 가해자 피해자 과실비율 다 따졌는데 소송까지 갔어요");
        assertThat(r.crisis()).isFalse();
        assertThat(r.patterns()).isEmpty();
    }

    @Test
    void nullAndEmptyAreSafe() {
        assertThat(guard.scan(null).crisis()).isFalse();
        assertThat(guard.scan("").crisis()).isFalse();
    }
}
