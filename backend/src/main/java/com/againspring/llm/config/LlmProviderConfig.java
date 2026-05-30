package com.againspring.llm.config;

import com.againspring.llm.LLMProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * LLM Provider 라우팅 설정.
 * 대화(chat)와 리포트(report)가 각각 다른 provider를 사용할 수 있도록 설정.
 *
 * 환경변수:
 *   - llm.chat.provider: 대화 provider ("remote", "claude-code", "mock", "claude-api")
 *   - llm.report.provider: 리포트 provider ("remote", "claude-code", "mock")
 *
 * @Primary로 지정된 reportLlmProvider는 @Qualifier 없는 모든 주입의 기본값.
 */
@Slf4j
@Configuration
public class LlmProviderConfig {

    @Value("${llm.chat.provider:claude-code}")
    private String chatProvider;

    @Value("${llm.report.provider:claude-code}")
    private String reportProvider;

    /**
     * 대화(chat) 전용 provider.
     * Qualifer: "chatLlmProvider"
     * 기본값: claude-code
     *
     * Phase 3에서 claude-api 추가 예정 — 지금은 ApplicationContext로 안전하게 참조.
     */
    @Bean
    @Qualifier("chatLlmProvider")
    public LLMProvider chatLlmProvider(
            @Qualifier("remoteLlmProvider") LLMProvider remote,
            @Qualifier("claudeCodeBridge") LLMProvider claudeCode,
            @Qualifier("mockLlmProvider") LLMProvider mock,
            ApplicationContext ctx) {

        LLMProvider selected = switch (chatProvider) {
            case "remote" -> {
                log.info("Chat LLM provider: remote");
                yield remote;
            }
            case "claude-api" -> {
                try {
                    log.info("Chat LLM provider: claude-api");
                    yield (LLMProvider) ctx.getBean("claudeApiProvider");
                } catch (Exception e) {
                    log.warn("claudeApiProvider not available yet, falling back to claude-code");
                    yield claudeCode;
                }
            }
            case "mock" -> {
                log.info("Chat LLM provider: mock");
                yield mock;
            }
            default -> {
                log.info("Chat LLM provider: claude-code (default)");
                yield claudeCode;
            }
        };

        return selected;
    }

    /**
     * 리포트(report) 전용 provider.
     * Qualifier: "reportLlmProvider"
     * @Primary로 지정 — @Qualifier 없는 ReportGenerationService 등의 기본값.
     * 기본값: claude-code
     */
    @Bean
    @Primary
    @Qualifier("reportLlmProvider")
    public LLMProvider reportLlmProvider(
            @Qualifier("remoteLlmProvider") LLMProvider remote,
            @Qualifier("claudeCodeBridge") LLMProvider claudeCode,
            @Qualifier("mockLlmProvider") LLMProvider mock) {

        LLMProvider selected = switch (reportProvider) {
            case "remote" -> {
                log.info("Report LLM provider: remote");
                yield remote;
            }
            case "mock" -> {
                log.info("Report LLM provider: mock");
                yield mock;
            }
            default -> {
                log.info("Report LLM provider: claude-code (default)");
                yield claudeCode;
            }
        };

        return selected;
    }
}
