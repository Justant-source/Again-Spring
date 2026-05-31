package com.againspring.service.marketing.social;

import com.againspring.domain.marketing.SocialCredential;
import com.againspring.repository.marketing.SocialCredentialRepository;
import com.againspring.security.crypto.SocialCryptoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 소셜 플랫폼 자격증 관리 서비스
 * 암호화/복호화 처리 포함
 */
@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "app.features.marketing.enabled", havingValue = "true")
public class SocialCredentialService {

    private final SocialCredentialRepository credentialRepository;
    private final SocialCryptoService socialCryptoService;

    /**
     * 플랫폼 자격증 저장 (암호화)
     */
    public void saveCredentials(String platform, String email, String password) {
        try {
            SocialCredential credential = credentialRepository.findByPlatform(platform)
                    .orElseGet(() -> SocialCredential.builder().platform(platform).build());

            credential.setEmailEnc(socialCryptoService.encryptString(email));
            credential.setPasswordEnc(socialCryptoService.encryptString(password));

            credentialRepository.save(credential);
            log.info("[SOCIAL_CRED] Credentials saved for platform={}", platform);
        } catch (GeneralSecurityException e) {
            log.error("[SOCIAL_CRED] Encryption failed for platform={}: {}", platform, e.getMessage());
            throw new RuntimeException("Failed to save credentials: " + e.getMessage(), e);
        }
    }

    /**
     * 플랫폼별 자격증 설정 상태 조회
     */
    public List<Map<String, Object>> getCredentialStatus() {
        return List.of("X", "INSTAGRAM").stream()
                .map(platform -> {
                    Map<String, Object> status = new HashMap<>();
                    status.put("platform", platform);
                    status.put("configured", credentialRepository.findByPlatform(platform).isPresent());
                    return status;
                })
                .toList();
    }

    /**
     * 플랫폼 자격증 복호화 조회
     */
    public Map<String, Object> decryptCredentials(String platform) {
        try {
            SocialCredential credential = credentialRepository.findByPlatform(platform)
                    .orElseThrow(() -> new RuntimeException("Credentials not configured for platform: " + platform));

            Map<String, Object> decrypted = new HashMap<>();
            decrypted.put("email", socialCryptoService.decryptString(credential.getEmailEnc()));
            decrypted.put("password", socialCryptoService.decryptString(credential.getPasswordEnc()));

            return decrypted;
        } catch (GeneralSecurityException e) {
            log.error("[SOCIAL_CRED] Decryption failed for platform={}: {}", platform, e.getMessage());
            throw new RuntimeException("Failed to decrypt credentials: " + e.getMessage(), e);
        }
    }
}
