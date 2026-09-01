package com.againspring.api.internal;

import com.againspring.marketing.AsmProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Constant-time Bearer check against {@code ASM_CALLBACK_TOKEN} ({@link AsmProperties#getCallbackToken()}).
 * Shared by ASM callback/redrive and persona export.
 */
@Component
@RequiredArgsConstructor
public class InternalTokenGuard {

    private final AsmProperties asmProperties;

    public boolean isAuthorized(String authHeader) {
        String expected = "Bearer " + asmProperties.getCallbackToken();
        return authHeader != null && constantTimeEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                authHeader.getBytes(StandardCharsets.UTF_8));
    }

    private static boolean constantTimeEqual(byte[] a, byte[] b) {
        if (a.length != b.length) {
            return false;
        }
        byte result = 0;
        for (int i = 0; i < a.length; i++) {
            result |= (byte) (a[i] ^ b[i]);
        }
        return result == 0;
    }
}
