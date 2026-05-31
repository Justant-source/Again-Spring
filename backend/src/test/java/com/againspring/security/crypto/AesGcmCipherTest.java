package com.againspring.security.crypto;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link AesGcmCipher} stateless encryption utility.
 *
 * <p>Tests AES-256-GCM encryption/decryption without Spring context.
 */
@DisplayName("AesGcmCipher")
class AesGcmCipherTest {

    /**
     * Generate a random 256-bit (32-byte) AES key.
     */
    private static SecretKeySpec generateAesKey() {
        byte[] keyBytes = new byte[32];
        new SecureRandom().nextBytes(keyBytes);
        return new SecretKeySpec(keyBytes, 0, keyBytes.length, "AES");
    }

    @Test
    @DisplayName("encrypt and decrypt roundtrip with UTF-8 plaintext")
    void testRoundTrip() throws GeneralSecurityException {
        // Arrange
        var key = generateAesKey();
        String plaintext = "안녕하세요 테스트";
        byte[] plaintextBytes = plaintext.getBytes(StandardCharsets.UTF_8);

        // Act
        byte[] encryptedBytes = AesGcmCipher.encrypt(plaintextBytes, key);
        String encryptedBase64 = new String(encryptedBytes, StandardCharsets.UTF_8);
        byte[] decrypted = AesGcmCipher.decrypt(encryptedBase64, key);

        // Assert
        assertArrayEquals(plaintextBytes, decrypted, "Decrypted plaintext should match original");
    }

    @Test
    @DisplayName("detect tampering: flipped ciphertext byte raises GeneralSecurityException")
    void testTamperDetected() throws GeneralSecurityException {
        // Arrange
        var key = generateAesKey();
        String plaintext = "hello";
        byte[] plaintextBytes = plaintext.getBytes(StandardCharsets.UTF_8);
        byte[] encryptedBytes = AesGcmCipher.encrypt(plaintextBytes, key);
        String encryptedBase64 = new String(encryptedBytes, StandardCharsets.UTF_8);

        // Tamper: decode base64, flip a bit in ciphertext (skip IV, flip byte at position 12)
        byte[] decodedBytes = Base64.getDecoder().decode(encryptedBase64);
        if (decodedBytes.length > 12) {
            decodedBytes[12] ^= 0x01; // Flip one bit
        }
        String tamperedBase64 = Base64.getEncoder().encodeToString(decodedBytes);

        // Act & Assert
        assertThrows(
                GeneralSecurityException.class,
                () -> AesGcmCipher.decrypt(tamperedBase64, key),
                "Tampering should raise GeneralSecurityException (AEADBadTagException or BadPaddingException)");
    }

    @Test
    @DisplayName("decrypt with wrong key raises GeneralSecurityException")
    void testWrongKeyFails() throws GeneralSecurityException {
        // Arrange
        var keyA = generateAesKey();
        var keyB = generateAesKey();
        String plaintext = "secret message";
        byte[] plaintextBytes = plaintext.getBytes(StandardCharsets.UTF_8);

        // Encrypt with key A
        byte[] encryptedBytes = AesGcmCipher.encrypt(plaintextBytes, keyA);
        String encryptedBase64 = new String(encryptedBytes, StandardCharsets.UTF_8);

        // Act & Assert: Decrypt with wrong key B
        assertThrows(
                GeneralSecurityException.class,
                () -> AesGcmCipher.decrypt(encryptedBase64, keyB),
                "Wrong key should raise GeneralSecurityException");
    }
}
