package com.againspring.security.crypto;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Canonical vault key → Spring/env property aliases injected at boot.
 */
public final class SecretVaultKeys {

    private SecretVaultKeys() {}

    /** vault key → list of property names to set (first is primary). */
    public static Map<String, String[]> propertyAliases() {
        Map<String, String[]> m = new LinkedHashMap<>();
        m.put("jwt.secret", new String[] {"jwt.secret", "JWT_SECRET"});
        m.put(
                "oauth.google.client_secret",
                new String[] {"oauth2.google.client-secret", "GOOGLE_CLIENT_SECRET"});
        m.put(
                "oauth.kakao.client_secret",
                new String[] {"oauth2.kakao.client-secret", "KAKAO_CLIENT_SECRET"});
        m.put(
                "oauth.naver.client_secret",
                new String[] {"oauth2.naver.client-secret", "NAVER_CLIENT_SECRET"});
        m.put("mail.gmail_app_password", new String[] {"spring.mail.password", "GMAIL_APP_PASSWORD"});
        m.put("llm.anthropic_api_key", new String[] {"ANTHROPIC_API_KEY"});
        m.put("ai_user.bot_password", new String[] {"AI_USER_BOT_PASSWORD"});
        m.put("ai_user.internal_token", new String[] {"ai-user.internal-token", "AI_USER_INTERNAL_TOKEN"});
        m.put("sync.dev_mariadb_password", new String[] {"DEV_MARIADB_PASSWORD"});
        m.put("asm.api_token", new String[] {"asm.api-token", "ASM_API_TOKEN"});
        m.put("asm.callback_token", new String[] {"asm.callback-token", "ASM_CALLBACK_TOKEN"});
        m.put("telegram.bot_token", new String[] {"telegram.bot-token", "TELEGRAM_BOT_TOKEN"});
        m.put("telegram.chat_id", new String[] {"telegram.chat-id", "TELEGRAM_CHAT_ID"});
        return m;
    }

    /** Prefix for GitHub PATs: {@code github.pat.<username>}. */
    public static final String GITHUB_PAT_PREFIX = "github.pat.";
}
