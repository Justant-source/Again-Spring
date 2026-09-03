package com.againspring.service.ai;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class LlmErrorSignaturesTest {
    @Test
    void sharedJsonDrivesAllBackendCopies() {
        assertThat(LlmErrorSignatures.get().signatures()).contains("credit balance", "permission_error");
        assertThat(AiCorrectionService.isErrorSignature("Your credit balance is too low")).isTrue();
        assertThat(AiCorrectionService.isErrorSignature("permission_error")).isTrue();   // 이전 복사본에 없던 시그니처
        assertThat(com.againspring.marketing.XPersonaLearnService.looksLikeLlmError("as an AI I can't")).isTrue();
        assertThat(com.againspring.marketing.XPersonaLearnService.looksLikeLlmError("힘빠지긴 할듯")).isFalse();
    }

    @Test
    void containsSignature_apiErrorWithSpace() {
        assertThat(LlmErrorSignatures.get().containsSignature("api error: something broke")).isTrue();
    }
}
