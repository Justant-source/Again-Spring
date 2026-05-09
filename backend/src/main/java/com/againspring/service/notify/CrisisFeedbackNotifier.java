package com.againspring.service.notify;

import com.againspring.domain.Feedback;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Slf4j
@Component
public class CrisisFeedbackNotifier {

    @Value("${app.crisis.webhook-url:}")
    private String webhookUrl;

    @Value("${app.crisis.email:}")
    private String crisisEmail;

    private final RestTemplate restTemplate = new RestTemplate();

    public void notifyIfCrisis(Feedback feedback) {
        if (!"crisis".equals(feedback.getCategory())) return;

        log.warn("[CRISIS_FEEDBACK] id={} userId={} content={}",
                feedback.getId(), feedback.getUserId(),
                feedback.getContent().substring(0, Math.min(100, feedback.getContent().length())));

        if (webhookUrl != null && !webhookUrl.isBlank()) {
            try {
                Map<String, Object> payload = Map.of(
                        "text", "[다시봄 위기 의견] id=" + feedback.getId()
                                + "\n내용: " + feedback.getContent().substring(0, Math.min(200, feedback.getContent().length())),
                        "feedback_id", feedback.getId()
                );
                restTemplate.postForEntity(webhookUrl, payload, String.class);
            } catch (Exception e) {
                log.error("[CRISIS_FEEDBACK] webhook 전송 실패: {}", e.getMessage());
            }
        }
    }
}
