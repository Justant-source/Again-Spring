package com.againspring.aiuser.llm.service;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class LlmErrorSignaturesTest {
    @Test
    void loadsSharedJsonAndMatchesRepresentativeStrings() {
        LlmErrorSignatures s = LlmErrorSignatures.get();
        assertThat(s.signatures()).contains("credit balance", "permission_error", "i can't help with this request");
        assertThat(s.promptLeakPatterns()).hasSize(9);
        assertThat(s.koreanRatioMin()).isEqualTo(0.10);
        assertThat(s.containsSignature("your credit balance is too low")).isTrue();
        assertThat(s.hasInsufficientKorean("I appreciate the context but I cannot write this comment for you.")).isTrue();
        assertThat(s.hasInsufficientKorean("남편이 어제 또 늦게 들어왔는데 진짜 화나더라구요")).isFalse();
        assertThat(s.hasPromptLeak("본문\n적용 처리 메모\n- 트리거: x")).isTrue();
    }

    @Test
    void legacyFacadeStillDetects() {
        assertThat(LlmErrorSignature.looksLikeProviderError("permission_error: not allowed")).isTrue();
        assertThat(LlmErrorSignature.looksLikeProviderError("오늘 진짜 열받아서 글 씁니다 ㅋㅋ")).isFalse();
    }
}
