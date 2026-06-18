package com.againspring.aiuser.llm.health;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class CodexCliHealthIndicator implements HealthIndicator {

    @Value("${llm.worker.codex-binary-path:codex}")
    private String codexBinaryPath;

    @Override
    public Health health() {
        try {
            ProcessBuilder pb = new ProcessBuilder(codexBinaryPath, "--version");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes()).trim();
            int exitCode = p.waitFor();

            if (exitCode == 0) {
                return Health.up().withDetail("codex-version", output).build();
            } else {
                log.warn("codex --version exited with code {}: {}", exitCode, output);
                return Health.down().withDetail("exitCode", exitCode).withDetail("output", output).build();
            }
        } catch (Exception e) {
            log.error("Codex CLI health check failed: {}", e.getMessage());
            return Health.down().withException(e).build();
        }
    }
}
