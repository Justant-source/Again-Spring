package com.againspring.llmworker.health;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ClaudeCliHealthIndicator implements HealthIndicator {

    @Value("${llm.worker.claude-binary-path:claude}")
    private String claudeBinaryPath;

    @Override
    public Health health() {
        try {
            ProcessBuilder pb = new ProcessBuilder(claudeBinaryPath, "--version");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes()).trim();
            int exitCode = p.waitFor();

            if (exitCode == 0) {
                return Health.up().withDetail("claude-version", output).build();
            } else {
                log.warn("claude --version exited with code {}: {}", exitCode, output);
                return Health.down().withDetail("exitCode", exitCode).withDetail("output", output).build();
            }
        } catch (Exception e) {
            log.error("Claude CLI health check failed: {}", e.getMessage());
            return Health.down().withException(e).build();
        }
    }
}
