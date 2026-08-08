package com.againspring.security.crypto;

import com.againspring.domain.EncryptedSecret;
import com.againspring.repository.EncryptedSecretRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read/write AES-GCM secrets in {@code encrypted_secret}. Never logs plaintext.
 */
@Service
public class SecretVaultService {

    private final EncryptedSecretRepository repository;
    private final SecretKey masterKey;

    public SecretVaultService(
            EncryptedSecretRepository repository,
            @Value("${AS_SECRET_MASTER_KEY:}") String masterKeyBase64) {
        this.repository = repository;
        this.masterKey =
                (masterKeyBase64 == null || masterKeyBase64.isBlank())
                        ? null
                        : SecretMasterKey.fromBase64(masterKeyBase64);
    }

    public boolean isConfigured() {
        return masterKey != null;
    }

    @Transactional(readOnly = true)
    public Optional<String> getPlain(String secretKey) {
        requireKey();
        return repository
                .findById(secretKey)
                .map(
                        row -> {
                            try {
                                byte[] plain = AesGcmCipher.decrypt(row.getEncBlob(), masterKey);
                                return new String(plain, StandardCharsets.UTF_8);
                            } catch (Exception e) {
                                throw new IllegalStateException(
                                        "Failed to decrypt secret: " + secretKey, e);
                            }
                        });
    }

    @Transactional
    public void putPlain(String secretKey, String plaintext) {
        requireKey();
        if (plaintext == null) {
            throw new IllegalArgumentException("plaintext must not be null for " + secretKey);
        }
        try {
            byte[] enc =
                    AesGcmCipher.encrypt(plaintext.getBytes(StandardCharsets.UTF_8), masterKey);
            String blob = new String(enc, StandardCharsets.US_ASCII);
            EncryptedSecret row =
                    repository
                            .findById(secretKey)
                            .orElse(EncryptedSecret.builder().secretKey(secretKey).build());
            row.setEncBlob(blob);
            row.setUpdatedAt(Instant.now());
            repository.save(row);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to encrypt secret: " + secretKey, e);
        }
    }

    @Transactional(readOnly = true)
    public Map<String, Boolean> listPresence() {
        Map<String, Boolean> out = new LinkedHashMap<>();
        for (EncryptedSecret row : repository.findAll()) {
            out.put(row.getSecretKey(), true);
        }
        return out;
    }

    private void requireKey() {
        if (masterKey == null) {
            throw new IllegalStateException(SecretMasterKey.ENV_NAME + " is not set");
        }
    }
}
