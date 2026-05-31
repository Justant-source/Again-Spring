package com.againspring.service.marketing.social;

import com.againspring.domain.marketing.MarketingContent;
import com.againspring.domain.marketing.SocialPublishResult;
import com.againspring.domain.marketing.SocialSession;
import com.againspring.repository.marketing.MarketingContentRepository;
import com.againspring.repository.marketing.SocialPublishResultRepository;
import com.againspring.repository.marketing.SocialSessionRepository;
import com.againspring.security.crypto.SocialCryptoService;
import com.againspring.service.notify.SocialOperatorNotifier;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 소셜 플랫폼 발행 비동기 실행기
 * @Async로 스레드풀에서 실행
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "app.features.marketing.enabled", havingValue = "true")
public class SocialPublishExecutor {

    private final SocialPosterClient posterClient;
    private final SocialCredentialService credentialService;
    private final SocialSessionRepository sessionRepository;
    private final SocialPublishResultRepository resultRepository;
    private final MarketingContentRepository contentRepository;
    private final SocialCryptoService cryptoService;
    private final SocialOperatorNotifier notifier;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Async("socialExecutor")
    public void executePublish(Long contentId, List<String> targetPlatforms, String linkMode) {
        log.info("[SOCIAL_PUBLISH] Starting publish for contentId={} platforms={}", contentId, targetPlatforms);

        for (String platform : targetPlatforms) {
            try {
                SocialSession session = sessionRepository.findByPlatform(platform)
                        .orElseThrow(() -> new RuntimeException("No session found for platform: " + platform));

                String storageStateJson;
                try {
                    storageStateJson = cryptoService.decryptString(session.getStorageStateEnc());
                } catch (GeneralSecurityException e) {
                    throw new RuntimeException("Failed to decrypt session for platform: " + platform, e);
                }
                Map<String, Object> credentials = credentialService.decryptCredentials(platform);
                MarketingContent content = contentRepository.findById(contentId)
                        .orElseThrow(() -> new RuntimeException("Content not found: " + contentId));

                Map<String, Object> requestBody = buildRequestBody(platform, content, linkMode, storageStateJson, credentials);
                SocialPosterClient.PublishOutcome outcome = "X".equals(platform)
                        ? posterClient.publishX(requestBody)
                        : posterClient.publishInstagram(requestBody);

                SocialPublishResult result = resultRepository.findByContentIdAndPlatform(contentId, MarketingContent.Platform.valueOf(platform))
                        .orElseThrow(() -> new RuntimeException("Result record not found for contentId=" + contentId + ", platform=" + platform));

                if (outcome.ok()) {
                    result.setState(SocialPublishResult.ResultState.SUCCEEDED);
                    result.setPublishedUrl(outcome.url());
                    if (outcome.updatedStorageState() != null) {
                        try {
                            session.setStorageStateEnc(cryptoService.encryptString(outcome.updatedStorageState()));
                            session.setLastUsedAt(Instant.now());
                            sessionRepository.save(session);
                        } catch (GeneralSecurityException e) {
                            log.warn("[SOCIAL_PUBLISH] Failed to encrypt updated storage state: {}", e.getMessage());
                        }
                    }
                    log.info("[SOCIAL_PUBLISH] SUCCEEDED platform={} contentId={} url={}", platform, contentId, outcome.url());
                } else {
                    result.setState(SocialPublishResult.ResultState.FAILED);
                    result.setErrorReason(outcome.error());
                    if (outcome.needsReseed()) {
                        session.setStatus(SocialSession.SessionStatus.EXPIRED);
                        try {
                            sessionRepository.save(session);
                        } catch (Exception e) {
                            log.warn("[SOCIAL_PUBLISH] Failed to save session expiration: {}", e.getMessage());
                        }
                        notifier.notifySessionExpired(platform, contentId);
                    }
                    log.warn("[SOCIAL_PUBLISH] FAILED platform={} contentId={} error={}", platform, contentId, outcome.error());
                }
                resultRepository.save(result);

            } catch (Exception e) {
                log.error("[SOCIAL_PUBLISH] Exception for platform={} contentId={}: {}", platform, contentId, e.getMessage(), e);
                resultRepository.findByContentIdAndPlatform(contentId, MarketingContent.Platform.valueOf(platform))
                        .ifPresent(result -> {
                            result.setState(SocialPublishResult.ResultState.FAILED);
                            result.setErrorReason(e.getMessage());
                            resultRepository.save(result);
                        });
            }
        }

        updateContentStatus(contentId);
    }

    /**
     * 모든 플랫폼 발행 결과 기반 콘텐츠 상태 업데이트
     */
    private void updateContentStatus(Long contentId) {
        List<SocialPublishResult> results = resultRepository.findByContentId(contentId);
        if (results.isEmpty()) {
            return;
        }

        long succeeded = results.stream()
                .filter(r -> r.getState() == SocialPublishResult.ResultState.SUCCEEDED)
                .count();
        long failed = results.stream()
                .filter(r -> r.getState() == SocialPublishResult.ResultState.FAILED)
                .count();

        MarketingContent content = contentRepository.findById(contentId)
                .orElseThrow(() -> new RuntimeException("Content not found: " + contentId));

        if (succeeded == results.size()) {
            // 모든 플랫폼 성공
            content.setStatus(MarketingContent.Status.PUBLISHED);
            content.setPublishedAt(Instant.now());
            results.stream()
                    .filter(r -> r.getState() == SocialPublishResult.ResultState.SUCCEEDED)
                    .findFirst()
                    .ifPresent(r -> content.setPublishedUrl(r.getPublishedUrl()));
        } else if (failed == results.size()) {
            // 모든 플랫폼 실패
            content.setStatus(MarketingContent.Status.FAILED);
            notifier.notifyAllFailed(contentId);
        } else {
            // 일부만 성공 (혼합)
            content.setStatus(MarketingContent.Status.PARTIAL);
        }

        contentRepository.save(content);
    }

    /**
     * 플랫폼별 요청 본문 생성
     */
    private Map<String, Object> buildRequestBody(
            String platform,
            MarketingContent content,
            String linkMode,
            String storageStateJson,
            Map<String, Object> credentials) {

        if ("X".equals(platform)) {
            List<String> tweets = splitIntoTweets(content.getBodyText(), 270);
            Map<String, Object> contentMap = new HashMap<>();
            contentMap.put("tweets", tweets);
            contentMap.put("linkUrl", "https://againspring.net");
            contentMap.put("linkMode", linkMode != null ? linkMode : "last_tweet");

            Map<String, Object> request = new HashMap<>();
            request.put("storageState", storageStateJson);
            request.put("credentials", credentials);
            request.put("content", contentMap);
            return request;

        } else {
            // INSTAGRAM
            String caption = content.getBodyText();
            if (content.getHashtags() != null && !content.getHashtags().isBlank()) {
                caption = caption + "\n\n" + content.getHashtags();
            }

            String imageBase64 = extractFirstImageBase64(content);
            Map<String, Object> contentMap = new HashMap<>();
            contentMap.put("caption", caption);
            contentMap.put("imageBase64", imageBase64);
            contentMap.put("imageFilename", "post.png");

            Map<String, Object> request = new HashMap<>();
            request.put("storageState", storageStateJson);
            request.put("credentials", credentials);
            request.put("content", contentMap);
            return request;
        }
    }

    /**
     * 텍스트를 최대 길이 기반 트윗으로 분할
     */
    private List<String> splitIntoTweets(String text, int maxLen) {
        if (text == null || text.isBlank()) {
            return List.of("");
        }

        if (text.length() <= maxLen) {
            return List.of(text);
        }

        List<String> tweets = new ArrayList<>();
        String[] sentences = text.split("(?<=[.!?。]\\s)");

        StringBuilder current = new StringBuilder();
        for (String sentence : sentences) {
            if (current.length() + sentence.length() > maxLen && !current.isEmpty()) {
                tweets.add(current.toString().trim());
                current = new StringBuilder(sentence);
            } else {
                current.append(sentence);
            }
        }

        if (!current.isEmpty()) {
            tweets.add(current.toString().trim());
        }

        return tweets.isEmpty()
                ? List.of(text.substring(0, Math.min(text.length(), maxLen)))
                : tweets;
    }

    /**
     * imagePaths에서 첫 번째 이미지 Base64 추출
     */
    private String extractFirstImageBase64(MarketingContent content) {
        if (content.getImagePaths() == null || content.getImagePaths().isBlank()) {
            return null;
        }

        try {
            String paths = content.getImagePaths().trim();
            String firstPath;

            if (paths.startsWith("[")) {
                // JSON 배열 파싱
                List<?> pathList = objectMapper.readValue(paths, List.class);
                firstPath = pathList.isEmpty() ? null : (String) pathList.get(0);
            } else {
                // 쉼표 구분
                firstPath = paths.split(",")[0].trim();
            }

            if (firstPath == null || firstPath.isBlank()) {
                return null;
            }

            byte[] bytes = Files.readAllBytes(Path.of(firstPath));
            return java.util.Base64.getEncoder().encodeToString(bytes);

        } catch (IOException e) {
            log.warn("[SOCIAL_PUBLISH] Could not read image: {}", e.getMessage());
            return null;
        }
    }
}
