package com.againspring.security.crypto;

import java.util.Base64;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

/**
 * Loads {@code AS_SECRET_MASTER_KEY} (base64 32 bytes) for {@link AesGcmCipher}.
 */
public final class SecretMasterKey {

    public static final String ENV_NAME = "AS_SECRET_MASTER_KEY";
    private static final int KEY_BYTES = 32;

    private SecretMasterKey() {}

    public static SecretKey fromBase64(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalStateException(ENV_NAME + " is not set (openssl rand -base64 32)");
        }
        byte[] key;
        try {
            key = Base64.getDecoder().decode(raw.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(ENV_NAME + " must be valid base64", e);
        }
        if (key.length != KEY_BYTES) {
            throw new IllegalStateException(
                    ENV_NAME + " must decode to exactly " + KEY_BYTES + " bytes (got " + key.length + ")");
        }
        return new SecretKeySpec(key, "AES");
    }
}
