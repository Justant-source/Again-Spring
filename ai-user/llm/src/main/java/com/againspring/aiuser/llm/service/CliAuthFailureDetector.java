package com.againspring.aiuser.llm.service;

import java.util.List;
import java.util.Locale;

/** CLI stderr 꼬리에서 세션 만료/조직 차단/키 무효를 분류한다. 콘텐츠 시그니처(LlmErrorSignature)와 별개. */
public final class CliAuthFailureDetector {
    private static final List<String> SIGNS = List.of(
        "not logged in", "please run /login", "run `claude login`",
        "organization has disabled", "subscription access",
        "authentication_error", "invalid api key", "invalid x-api-key",
        "oauth token", "token has expired", "invalid_grant", "refresh token",
        "401 unauthorized", "http 401", "status 401"
    );

    private CliAuthFailureDetector() {}

    public static boolean isAuthFailure(String stderrTail) {
        if (stderrTail == null || stderrTail.isBlank()) return false;
        String lower = stderrTail.toLowerCase(Locale.ROOT);
        return SIGNS.stream().anyMatch(lower::contains);
    }
}
