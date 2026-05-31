package com.againspring.api.admin.marketing;

import com.againspring.domain.marketing.MarketingContent;
import com.againspring.domain.marketing.SocialPublishResult;
import com.againspring.repository.marketing.MarketingContentRepository;
import com.againspring.repository.marketing.SocialPublishResultRepository;
import com.againspring.repository.marketing.SocialSessionRepository;
import com.againspring.security.crypto.SocialCryptoService;
import com.againspring.service.marketing.social.DuplicatePublishException;
import com.againspring.service.marketing.social.SocialCredentialService;
import com.againspring.service.marketing.social.SocialPosterClient;
import com.againspring.service.marketing.social.SocialPublishService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Admin API 컨트롤러: 소셜 플랫폼 발행 관리
 * - 자격증 설정 (X, Instagram)
 * - 세션 Seed (Playwright storageState)
 * - 콘텐츠 발행 시작
 * - 발행 상태 조회
 */
@RestController
@RequestMapping("/api/admin/marketing/social")
@ConditionalOnProperty(name = "app.features.marketing.enabled", havingValue = "true")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "SocialPublish", description = "소셜 자동 포스팅 관리")
@SecurityRequirement(name = "bearerAuth")
public class SocialPublishController {

    private final SocialPublishService publishService;
    private final SocialCredentialService credentialService;
    private final SocialPosterClient posterClient;
    private final SocialSessionRepository sessionRepository;
    private final SocialPublishResultRepository resultRepository;
    private final SocialCryptoService cryptoService;
    private final MarketingContentRepository contentRepository;

    /**
     * 콘텐츠 발행 시작
     * POST /api/admin/marketing/social/publish/{contentId}
     * Request: { "targets": ["X", "INSTAGRAM"], "linkMode": "last_tweet" }
     */
    @PostMapping("/publish/{contentId}")
    @Operation(summary = "Start publishing content", description = "Initiate publishing to selected platforms")
    public ResponseEntity<?> initiatePublish(
            @PathVariable Long contentId,
            @RequestBody Map<String, Object> request) {
        try {
            @SuppressWarnings("unchecked")
            List<String> targets = (List<String>) request.get("targets");
            String linkMode = (String) request.getOrDefault("linkMode", "last_tweet");

            List<SocialPublishResult> results = publishService.initiatePublish(contentId, targets, linkMode);

            return ResponseEntity.accepted().body(Map.of(
                    "contentId", contentId,
                    "results", results.stream()
                            .map(r -> Map.of(
                                    "platform", r.getPlatform().name(),
                                    "state", r.getState().name()
                            ))
                            .toList()
            ));
        } catch (DuplicatePublishException e) {
            return ResponseEntity.status(409).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 콘텐츠 발행 상태 조회
     * GET /api/admin/marketing/social/publish/{contentId}/status
     */
    @GetMapping("/publish/{contentId}/status")
    @Operation(summary = "Get publishing status", description = "Retrieve publishing status for all platforms")
    public ResponseEntity<?> getPublishStatus(@PathVariable Long contentId) {
        MarketingContent content = contentRepository.findById(contentId)
                .orElseThrow(() -> new RuntimeException("Content not found: " + contentId));
        List<SocialPublishResult> results = resultRepository.findByContentId(contentId);

        List<Map<String, Object>> resultMaps = results.stream()
                .map(r -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("platform", r.getPlatform().name());
                    m.put("state", r.getState().name());
                    if (r.getPublishedUrl() != null) {
                        m.put("publishedUrl", r.getPublishedUrl());
                    }
                    if (r.getErrorReason() != null) {
                        m.put("errorReason", r.getErrorReason());
                    }
                    if (r.getAttemptedAt() != null) {
                        m.put("attemptedAt", r.getAttemptedAt().toString());
                    }
                    return m;
                })
                .toList();

        return ResponseEntity.ok(Map.of(
                "contentId", contentId,
                "contentStatus", content.getStatus().name(),
                "results", resultMaps
        ));
    }

    /**
     * 플랫폼 자격증 저장
     * POST /api/admin/marketing/social/credentials
     * Request: { "platform": "X", "email": "...", "password": "..." }
     */
    @PostMapping("/credentials")
    @Operation(summary = "Save platform credentials", description = "Save encrypted credentials for a platform")
    public ResponseEntity<?> saveCredentials(@RequestBody Map<String, Object> request) {
        String platform = (String) request.get("platform");
        String email = (String) request.get("email");
        String password = (String) request.get("password");

        credentialService.saveCredentials(platform, email, password);
        log.info("[SOCIAL_CRED] Credentials saved for platform={}", platform);

        return ResponseEntity.ok(Map.of(
                "platform", platform,
                "configured", true
        ));
    }

    /**
     * 로그인 테스트 (Playwright headless)
     * POST /api/admin/marketing/social/test-login/{platform}
     */
    @PostMapping("/test-login/{platform}")
    @Operation(summary = "Test platform login", description = "Test login with stored credentials via headless browser")
    public ResponseEntity<?> testLogin(@PathVariable String platform) {
        try {
            Map<String, Object> credentials = credentialService.decryptCredentials(platform);
            Map<String, Object> result = posterClient.testLogin(platform, credentials);
            return ResponseEntity.ok(result);
        } catch (RuntimeException e) {
            return ResponseEntity.status(400).body(Map.of("ok", false, "error", e.getMessage()));
        }
    }

    /**
     * 자격증 설정 상태 조회
     * GET /api/admin/marketing/social/credentials/status
     */
    @GetMapping("/credentials/status")
    @Operation(summary = "Get credential status", description = "Check which platforms have credentials configured")
    public ResponseEntity<?> getCredentialStatus() {
        return ResponseEntity.ok(credentialService.getCredentialStatus());
    }

    /**
     * 세션 Seed (Playwright storageState 저장)
     * POST /api/admin/marketing/social/sessions
     * Request: { "platform": "X", "storageState": "{ ... }" }
     */
    @PostMapping("/sessions")
    @Operation(summary = "Seed browser session", description = "Save encrypted Playwright storage state")
    public ResponseEntity<?> seedSession(@RequestBody Map<String, Object> request) {
        try {
            String platform = (String) request.get("platform");
            String storageState = (String) request.get("storageState");

            var session = sessionRepository.findByPlatform(platform)
                    .orElse(new com.againspring.domain.marketing.SocialSession());

            session.setPlatform(platform);
            session.setStorageStateEnc(cryptoService.encryptString(storageState));
            session.setStatus(com.againspring.domain.marketing.SocialSession.SessionStatus.SEEDED);
            sessionRepository.save(session);

            log.info("[SOCIAL_SESSION] Session seeded for platform={}", platform);

            return ResponseEntity.ok(Map.of(
                    "platform", platform,
                    "status", "SEEDED"
            ));
        } catch (java.security.GeneralSecurityException e) {
            log.error("[SOCIAL_SESSION] Encryption failed: {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of("error", "Encryption failed: " + e.getMessage()));
        }
    }

    /**
     * 세션 상태 조회
     * GET /api/admin/marketing/social/sessions/status
     */
    @GetMapping("/sessions/status")
    @Operation(summary = "Get session status", description = "Check browser session status for all platforms")
    public ResponseEntity<?> getSessionStatus() {
        List<Map<String, Object>> statuses = List.of("X", "INSTAGRAM").stream()
                .map(platform -> {
                    Map<String, Object> statusMap = new HashMap<>();
                    statusMap.put("platform", platform);

                    sessionRepository.findByPlatform(platform).ifPresentOrElse(
                            session -> {
                                statusMap.put("status", session.getStatus().name());
                                statusMap.put("lastUsedAt", session.getLastUsedAt() != null
                                        ? session.getLastUsedAt().toString()
                                        : "");
                            },
                            () -> {
                                statusMap.put("status", "NOT_SEEDED");
                                statusMap.put("lastUsedAt", "");
                            }
                    );

                    return statusMap;
                })
                .toList();

        return ResponseEntity.ok(statuses);
    }
}
