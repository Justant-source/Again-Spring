package com.againspring.service.notify;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Slf4j
@Component
@ConditionalOnProperty(name = "app.features.marketing.enabled", havingValue = "true")
public class SocialOperatorNotifier {

    @Value("${app.social.webhook-url:}")
    private String webhookUrl;

    @Value("${app.social.email:}")
    private String socialEmail;

    private final RestTemplate restTemplate = new RestTemplate();

    public void notifySessionExpired(String platform, Long contentId) {
        log.warn("[SOCIAL_NOTIFY] session expired platform={} contentId={}", platform, contentId);
        sendWebhook("[다시봄 소셜] 세션 만료\n플랫폼: " + platform + "\n콘텐츠 ID: " + contentId + "\nseed-cli.js로 재시딩이 필요합니다.");
    }

    public void notifyAllFailed(Long contentId) {
        log.error("[SOCIAL_NOTIFY] all platforms failed contentId={}", contentId);
        sendWebhook("[다시봄 소셜] 전 플랫폼 발행 실패\n콘텐츠 ID: " + contentId + "\n원인을 확인하고 수동 발행을 검토해주세요.");
    }

    public void notifyHealthCheckFailed(String platform) {
        log.warn("[SOCIAL_NOTIFY] daily health check failed platform={}", platform);
        sendWebhook("[다시봄 소셜] 세션 헬스체크 실패\n플랫폼: " + platform + "\n세션이 만료되었을 수 있습니다. admin 설정에서 확인해주세요.");
    }

    private void sendWebhook(String message) {
        if (webhookUrl == null || webhookUrl.isBlank()) return;
        try {
            restTemplate.postForEntity(webhookUrl, Map.of("text", message), String.class);
        } catch (Exception e) {
            log.error("[SOCIAL_NOTIFY] webhook 전송 실패: {}", e.getMessage());
        }
    }
}
