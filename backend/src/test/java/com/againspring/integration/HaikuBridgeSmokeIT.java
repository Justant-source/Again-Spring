package com.againspring.integration;

import com.againspring.llm.bridge.ClaudeCodeBridge;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 실 Claude CLI를 한 번 호출해 ClaudeCodeBridge가 동작하는지 확인하는 스모크 테스트.
 *
 * - 기본 test 태스크에서 제외 (@Tag("haiku") + build.gradle.kts excludeTags)
 * - 실행: ./gradlew haikuSmoke  (호스트 ~/.claude 로그인 전제)
 * - 대화 품질은 검사하지 않음 — 응답이 비어있지 않으면 pass
 */
@Tag("haiku")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("haiku-smoke")
@TestPropertySource(properties = {
        "llm.provider=claude-code",
        "llm.claude-code.binary-path=claude"
})
class HaikuBridgeSmokeIT {

    @Autowired
    private ClaudeCodeBridge bridge;

    @Test
    void haikuRespondsToSimplePrompt() throws Exception {
        // claude CLI가 PATH에 있는지 확인 — 없으면 skip (CI 환경 등)
        boolean claudeAvailable = false;
        try {
            ProcessBuilder pb = new ProcessBuilder("which", "claude");
            pb.redirectErrorStream(true);
            claudeAvailable = pb.start().waitFor() == 0;
        } catch (Exception ignored) {}
        assumeTrue(claudeAvailable, "claude CLI가 PATH에 없어 스모크 테스트를 건너뜁니다");

        String response = bridge.invoke(
                "테스트입니다. 짧게 '안녕하세요'라고만 답해주세요.",
                "claude-haiku-4-5-20251001");

        assertThat(response)
                .as("ClaudeCodeBridge가 비어있지 않은 응답을 반환해야 함")
                .isNotBlank();
    }
}
