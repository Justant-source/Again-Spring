package com.againspring.security.crypto;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("SecretMasterKey + vault AES roundtrip")
class SecretMasterKeyTest {

    @Test
    void roundTripMatchesAesGcmCipher() throws Exception {
        byte[] raw = new byte[32];
        for (int i = 0; i < 32; i++) {
            raw[i] = (byte) i;
        }
        String b64 = Base64.getEncoder().encodeToString(raw);
        SecretKey key = SecretMasterKey.fromBase64(b64);
        byte[] enc = AesGcmCipher.encrypt("hello-vault".getBytes(StandardCharsets.UTF_8), key);
        String blob = new String(enc, StandardCharsets.US_ASCII);
        byte[] dec = AesGcmCipher.decrypt(blob, key);
        assertEquals("hello-vault", new String(dec, StandardCharsets.UTF_8));
    }

    @Test
    void rejectsWrongLength() {
        byte[] raw = new byte[16];
        String b64 = Base64.getEncoder().encodeToString(raw);
        assertThrows(IllegalStateException.class, () -> SecretMasterKey.fromBase64(b64));
    }

    @Test
    void pythonCompatibleFormat() throws Exception {
        // Same layout Python seed script uses: Base64(iv||ciphertext||tag)
        SecretKey key = new SecretKeySpec(new byte[32], "AES");
        byte[] enc = AesGcmCipher.encrypt("x".getBytes(StandardCharsets.UTF_8), key);
        byte[] decoded = Base64.getDecoder().decode(enc);
        assertEquals(12 + 1 + 16, decoded.length); // iv + 1 byte pt + 16 tag
        assertArrayEquals(
                "x".getBytes(StandardCharsets.UTF_8),
                AesGcmCipher.decrypt(new String(enc, StandardCharsets.US_ASCII), key));
    }
}
