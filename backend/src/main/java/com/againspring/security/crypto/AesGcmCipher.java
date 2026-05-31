package com.againspring.security.crypto;

import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Stateless AES-256-GCM cipher utility for credential encryption.
 *
 * <p>Uses AES/GCM/NoPadding with:
 * <ul>
 *   <li>12-byte random IV (per-encryption)
 *   <li>128-bit authentication tag
 *   <li>SecureRandom for IV generation
 * </ul>
 *
 * <p>Encryption format: Base64(iv || ciphertext || auth_tag)
 * <ul>
 *   <li>IV: first 12 bytes (raw binary)
 *   <li>Ciphertext + Tag: remaining bytes (Cipher output includes tag)
 * </ul>
 *
 * <p>On decryption failure (tampering or wrong key): {@link javax.crypto.AEADBadTagException}
 * or {@link javax.crypto.BadPaddingException} propagates uncaught.
 */
public class AesGcmCipher {

    private static final String CIPHER_ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH_BYTES = 12;
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /**
     * Encrypt plaintext using AES-256-GCM.
     *
     * @param plaintext the data to encrypt (UTF-8 bytes)
     * @param key the AES secret key (256-bit)
     * @return Base64-encoded bytes (iv || ciphertext || tag)
     * @throws java.security.GeneralSecurityException on cipher error
     */
    public static byte[] encrypt(byte[] plaintext, SecretKey key) throws java.security.GeneralSecurityException {
        // Generate random 12-byte IV
        byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
        SECURE_RANDOM.nextBytes(iv);

        // Initialize cipher in ENCRYPT mode
        Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
        GCMParameterSpec paramSpec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
        cipher.init(Cipher.ENCRYPT_MODE, key, paramSpec);

        // Encrypt plaintext (Cipher.doFinal includes authentication tag)
        byte[] ciphertext = cipher.doFinal(plaintext);

        // Concatenate IV and ciphertext
        byte[] encrypted = new byte[GCM_IV_LENGTH_BYTES + ciphertext.length];
        System.arraycopy(iv, 0, encrypted, 0, GCM_IV_LENGTH_BYTES);
        System.arraycopy(ciphertext, 0, encrypted, GCM_IV_LENGTH_BYTES, ciphertext.length);

        // Return Base64-encoded bytes
        return Base64.getEncoder().encode(encrypted);
    }

    /**
     * Decrypt ciphertext using AES-256-GCM.
     *
     * @param base64Ciphertext Base64-encoded (iv || ciphertext || tag)
     * @param key the AES secret key (256-bit)
     * @return decrypted plaintext bytes
     * @throws java.security.GeneralSecurityException on decryption failure or tampering
     *         (AEADBadTagException or BadPaddingException)
     * @throws IllegalArgumentException if base64 is malformed or too short
     */
    public static byte[] decrypt(String base64Ciphertext, SecretKey key)
            throws java.security.GeneralSecurityException {
        // Decode Base64
        byte[] encrypted = Base64.getDecoder().decode(base64Ciphertext);

        // Validate minimum length (IV + at least 1 byte of ciphertext + tag)
        if (encrypted.length < GCM_IV_LENGTH_BYTES) {
            throw new IllegalArgumentException(
                    "Encrypted data too short: expected at least " + GCM_IV_LENGTH_BYTES
                            + " bytes for IV, got " + encrypted.length);
        }

        // Extract IV
        byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
        System.arraycopy(encrypted, 0, iv, 0, GCM_IV_LENGTH_BYTES);

        // Extract ciphertext (includes authentication tag)
        byte[] ciphertext = new byte[encrypted.length - GCM_IV_LENGTH_BYTES];
        System.arraycopy(encrypted, GCM_IV_LENGTH_BYTES, ciphertext, 0, ciphertext.length);

        // Initialize cipher in DECRYPT mode
        Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
        GCMParameterSpec paramSpec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
        cipher.init(Cipher.DECRYPT_MODE, key, paramSpec);

        // Decrypt (Cipher.doFinal validates tag; throws AEADBadTagException on failure)
        return cipher.doFinal(ciphertext);
    }

    private AesGcmCipher() {
        // Stateless utility class
    }
}
