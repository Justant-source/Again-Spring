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

import org.springframework.beans.factory.annotation.Value;

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

    @Value("${app.features.marketing.image-dir:/tmp/marketing-images}")
    private String imageDir;

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
            // 250(가중치) — X 280 한도에서 링크(~24)·여유 확보. CJK는 2로 계산됨.
            List<String> tweets = splitIntoTweets(content.getBodyText(), 250);
            Map<String, Object> contentMap = new HashMap<>();
            contentMap.put("tweets", tweets);
            contentMap.put("linkUrl", "https://againspring.net");
            contentMap.put("linkMode", linkMode != null ? linkMode : "last_tweet");

            // 첫 번째 이미지 (TWEET_1 슬롯 우선, 없으면 첫 이미지)
            String coverBase64 = extractImageBase64BySlot(content, "TWEET_1");
            if (coverBase64 == null) {
                coverBase64 = extractFirstImageBase64(content);
            }
            if (coverBase64 != null) {
                contentMap.put("imageBase64", coverBase64);
                contentMap.put("imageFilename", "cover.png");
            }

            Map<String, Object> request = new HashMap<>();
            request.put("storageState", storageStateJson);
            request.put("credentials", credentials);
            request.put("content", contentMap);
            return request;

        } else {
            // INSTAGRAM — 카드뉴스 슬라이드 전체 전달
            String caption = content.getBodyText();
            if (content.getHashtags() != null && !content.getHashtags().isBlank()) {
                caption = caption + "\n\n" + content.getHashtags();
            }

            List<Map<String, Object>> images = extractAllImagesAsBase64List(content);
            Map<String, Object> contentMap = new HashMap<>();
            contentMap.put("caption", caption);
            if (!images.isEmpty()) {
                contentMap.put("images", images);
            }

            Map<String, Object> request = new HashMap<>();
            request.put("storageState", storageStateJson);
            request.put("credentials", credentials);
            request.put("content", contentMap);
            return request;
        }
    }

    /**
     * 텍스트를 트윗으로 분할.
     * ⚠️ X는 한글·한자·가나 등 CJK 문자를 2글자로 계산(280 한도). JS/Java .length(CJK=1)로
     *    세면 한글 트윗이 한도를 초과해 Post 버튼이 비활성화됨. → 가중치 기반으로 분할.
     *
     * @param maxLen 가중치 기준 최대 길이(280 미만 권장, 링크 여유 포함 ~250)
     */
    private List<String> splitIntoTweets(String text, int maxLen) {
        if (text == null || text.isBlank()) {
            return List.of("");
        }

        if (weightedLength(text) <= maxLen) {
            return List.of(text);
        }

        List<String> tweets = new ArrayList<>();
        // 문장 우선 분할, 한 문장이 너무 길면 가중치 기준으로 강제 분할
        String[] sentences = text.split("(?<=[.!?。]\\s)");

        StringBuilder current = new StringBuilder();
        for (String sentence : sentences) {
            for (String chunk : splitByWeight(sentence, maxLen)) {
                if (weightedLength(current.toString()) + weightedLength(chunk) > maxLen
                        && current.length() > 0) {
                    tweets.add(current.toString().trim());
                    current = new StringBuilder(chunk);
                } else {
                    current.append(chunk);
                }
            }
        }

        if (current.length() > 0) {
            tweets.add(current.toString().trim());
        }

        return tweets.isEmpty() ? List.of(text) : tweets;
    }

    /** X 가중치 길이: CJK 문자 = 2, 그 외 = 1 */
    private int weightedLength(String s) {
        int w = 0;
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            i += Character.charCount(cp);
            w += isCjk(cp) ? 2 : 1;
        }
        return w;
    }

    private boolean isCjk(int cp) {
        return (cp >= 0xAC00 && cp <= 0xD7A3)   // 한글 음절
                || (cp >= 0x1100 && cp <= 0x11FF)   // 한글 자모
                || (cp >= 0x3130 && cp <= 0x318F)   // 한글 호환 자모
                || (cp >= 0x4E00 && cp <= 0x9FFF)   // CJK 한자
                || (cp >= 0x3040 && cp <= 0x30FF)   // 히라가나+가타카나
                || (cp >= 0xFF00 && cp <= 0xFFEF);  // 전각 문자
    }

    /** 문장 하나가 maxLen(가중치)을 넘으면 공백/문자 경계로 강제 분할 */
    private List<String> splitByWeight(String sentence, int maxLen) {
        if (weightedLength(sentence) <= maxLen) {
            return List.of(sentence);
        }
        List<String> chunks = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String word : sentence.split("(?<= )")) { // 공백 뒤에서 분할(공백 유지)
            if (weightedLength(cur.toString()) + weightedLength(word) > maxLen && cur.length() > 0) {
                chunks.add(cur.toString());
                cur = new StringBuilder(word);
            } else {
                cur.append(word);
            }
            // 단어 자체가 maxLen 초과(긴 URL 등)면 문자 단위로 자름
            while (weightedLength(cur.toString()) > maxLen) {
                int cut = cur.length() / 2;
                chunks.add(cur.substring(0, cut));
                cur = new StringBuilder(cur.substring(cut));
            }
        }
        if (cur.length() > 0) chunks.add(cur.toString());
        return chunks;
    }

    /**
     * imagePaths에서 첫 번째 이미지 Base64 추출.
     * JSON 객체 배열 형식([{"filename":..., "role":..., "slot":...}])과 문자열 배열 형식 모두 지원.
     */
    private String extractFirstImageBase64(MarketingContent content) {
        if (content.getImagePaths() == null || content.getImagePaths().isBlank()) {
            return null;
        }
        try {
            String paths = content.getImagePaths().trim();
            if (paths.startsWith("[")) {
                List<?> pathList = objectMapper.readValue(paths, List.class);
                if (pathList.isEmpty()) return null;
                return readImageFromListItem(pathList.get(0));
            } else {
                // 쉼표 구분 레거시 형식
                String firstPath = paths.split(",")[0].trim();
                return readFileAsBase64(Path.of(firstPath));
            }
        } catch (Exception e) {
            log.warn("[SOCIAL_PUBLISH] Could not read first image: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 특정 slot의 이미지 Base64 추출 (예: "TWEET_1", "SLIDE_1")
     */
    private String extractImageBase64BySlot(MarketingContent content, String targetSlot) {
        if (content.getImagePaths() == null || content.getImagePaths().isBlank()) return null;
        try {
            String paths = content.getImagePaths().trim();
            if (!paths.startsWith("[")) return null;
            List<?> pathList = objectMapper.readValue(paths, List.class);
            for (Object item : pathList) {
                if (item instanceof Map<?, ?> map) {
                    Object slot = map.get("slot");
                    if (targetSlot.equals(slot != null ? slot.toString() : null)) {
                        return readImageFromListItem(item);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[SOCIAL_PUBLISH] Could not read image by slot={}: {}", targetSlot, e.getMessage());
        }
        return null;
    }

    /**
     * 모든 이미지를 Base64 리스트로 추출 (인스타 카드뉴스용)
     */
    private List<Map<String, Object>> extractAllImagesAsBase64List(MarketingContent content) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (content.getImagePaths() == null || content.getImagePaths().isBlank()) return result;
        try {
            String paths = content.getImagePaths().trim();
            if (!paths.startsWith("[")) return result;
            List<?> pathList = objectMapper.readValue(paths, List.class);
            for (Object item : pathList) {
                if (item instanceof Map<?, ?> map) {
                    Object fn = map.get("filename");
                    if (fn == null) continue;
                    try {
                        byte[] bytes = Files.readAllBytes(Path.of(imageDir, fn.toString()));
                        Object slotObj = map.get("slot");
                        result.add(Map.of(
                                "base64", java.util.Base64.getEncoder().encodeToString(bytes),
                                "filename", fn.toString(),
                                "slot", slotObj != null ? slotObj.toString() : ""
                        ));
                    } catch (IOException e) {
                        log.warn("[SOCIAL_PUBLISH] Could not read image {}: {}", fn, e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[SOCIAL_PUBLISH] Could not parse imagePaths for all images: {}", e.getMessage());
        }
        return result;
    }

    private String readImageFromListItem(Object item) throws IOException {
        if (item instanceof String s) {
            // 레거시: 이미 전체 경로 문자열
            return readFileAsBase64(Path.of(s));
        } else if (item instanceof Map<?, ?> map) {
            // 신규: {filename, role, slot, alt, order}
            Object fn = map.get("filename");
            if (fn == null) return null;
            return readFileAsBase64(Path.of(imageDir, fn.toString()));
        }
        return null;
    }

    private String readFileAsBase64(Path path) throws IOException {
        byte[] bytes = Files.readAllBytes(path);
        return java.util.Base64.getEncoder().encodeToString(bytes);
    }
}
