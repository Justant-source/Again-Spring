package com.againspring.security.crypto;

import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Spring-managed AES-256-GCM encryption service for social platform credentials.
 *
 * <p>Enabled only when {@code app.features.marketing.enabled=true} to prevent startup
 * errors in environments where the master key is not configured.
 *
 * <p>Requires environment variable: {@code app.social.master-key} = 32-byte Base64 string.
 * Generate with: {@code openssl rand -base64 32}
 *
 * <p>Usage:
 * <pre>
 *   String encrypted = socialCryptoService.encryptString("facebook_token_xyz");
 *   String decrypted = socialCryptoService.decryptString(encrypted);
 * </pre>
 *
 * <p>Logs only indicate operation (no key or plaintext material).
 */
@Component
@ConditionalOnProperty(name = "app.features.marketing.enabled", havingValue = "true")
@Slf4j
public class SocialCryptoService {

    private final String masterKeyBase64;
    private SecretKey masterKey;

    public SocialCryptoService(@Value("${app.social.master-key:}") String masterKeyBase64) {
        this.masterKeyBase64 = masterKeyBase64;
    }

    /**
     * Validate and initialize the master key on bean creation.
     *
     * @throws BeanCreationException if the master key is missing or wrong length
     */
    @PostConstruct
    void init() {
        if (masterKeyBase64 == null || masterKeyBase64.isBlank()) {
            throw new BeanCreationException(
                    "SocialCryptoService",
                    "SOCIAL_MASTER_KEY must be a 32-byte base64 string. "
                            + "Generate with: openssl rand -base64 32");
        }

        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(masterKeyBase64);
        } catch (IllegalArgumentException e) {
            throw new BeanCreationException(
                    "SocialCryptoService",
                    "SOCIAL_MASTER_KEY must be valid base64: " + e.getMessage(),
                    e);
        }

        if (keyBytes.length != 32) {
            throw new BeanCreationException(
                    "SocialCryptoService",
                    "SOCIAL_MASTER_KEY must be exactly 32 bytes (256 bits), "
                            + "got " + keyBytes.length + " bytes. "
                            + "Generate with: openssl rand -base64 32");
        }

        this.masterKey = new SecretKeySpec(keyBytes, 0, keyBytes.length, "AES");
        log.debug("SocialCryptoService initialized with 256-bit AES master key");
    }

    /**
     * Encrypt a string using AES-256-GCM.
     *
     * @param plaintext the string to encrypt (UTF-8)
     * @return Base64-encoded ciphertext (iv || encrypted data || tag)
     * @throws java.security.GeneralSecurityException on encryption error
     */
    public String encryptString(String plaintext) throws java.security.GeneralSecurityException {
        log.trace("[SOCIAL_CRYPTO] encrypt called");
        byte[] plaintextBytes = plaintext.getBytes(StandardCharsets.UTF_8);
        byte[] base64Bytes = AesGcmCipher.encrypt(plaintextBytes, masterKey);
        return new String(base64Bytes, StandardCharsets.UTF_8);
    }

    /**
     * Decrypt a Base64-encoded ciphertext using AES-256-GCM.
     *
     * @param base64Ciphertext Base64-encoded (iv || encrypted data || tag)
     * @return decrypted string (UTF-8)
     * @throws java.security.GeneralSecurityException on decryption failure or tampering
     * @throws IllegalArgumentException if base64 is malformed
     */
    public String decryptString(String base64Ciphertext) throws java.security.GeneralSecurityException {
        log.trace("[SOCIAL_CRYPTO] decrypt called");
        // Convert string to base64 bytes if needed, or pass directly
        byte[] decryptedBytes = AesGcmCipher.decrypt(base64Ciphertext, masterKey);
        return new String(decryptedBytes, StandardCharsets.UTF_8);
    }
}
