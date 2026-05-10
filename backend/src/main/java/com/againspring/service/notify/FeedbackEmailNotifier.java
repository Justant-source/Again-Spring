package com.againspring.service.notify;

import com.againspring.domain.Feedback;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * 사용자 피드백 수신 시 운영자 메일 알림.
 * - 모든 카테고리 → app.mail.support-email로 발송
 * - crisis 카테고리 → 제목에 [위기] 표시 (CrisisFeedbackNotifier가 별도 webhook 처리)
 * - 메일 발송 실패는 로그만 남기고 의견 저장은 정상 완료 (사용자 흐름 차단 금지)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FeedbackEmailNotifier {

    private static final Map<String, String> CATEGORY_LABEL = Map.of(
            "ui_bug", "UI 버그",
            "feature", "기능 제안",
            "content", "내용/카피",
            "crisis", "위기 신고",
            "praise", "칭찬",
            "other", "기타"
    );

    private static final DateTimeFormatter KST_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.of("Asia/Seoul"));

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:againspring2026@gmail.com}")
    private String mailFrom;

    @Value("${app.mail.from-name:다시봄 운영팀}")
    private String fromName;

    @Value("${app.mail.support-email:againspring2026@gmail.com}")
    private String supportEmail;

    @Async
    public void notifyNewFeedback(Feedback feedback) {
        String category = feedback.getCategory();
        String categoryLabel = CATEGORY_LABEL.getOrDefault(category, category);
        boolean isCrisis = "crisis".equals(category);
        String tag = isCrisis ? "[위기] " : "";

        String userInfo = feedback.getUserId() != null
                ? feedback.getUserId()
                : "익명";
        String createdAt = feedback.getCreatedAt() != null
                ? KST_FMT.format(feedback.getCreatedAt())
                : "(미상)";

        String subject = String.format("[다시봄 의견] %s[%s] #%d",
                tag, categoryLabel, feedback.getId());

        String body = String.format(
                "새 사용자 의견이 등록되었습니다.\n\n"
                + "─────────────────────\n"
                + "ID:       %d\n"
                + "카테고리: %s (%s)\n"
                + "사용자:   %s\n"
                + "수신일시: %s (KST)\n"
                + "─────────────────────\n\n"
                + "내용:\n%s\n\n"
                + "─────────────────────\n"
                + "관리자 페이지: https://dev.againspring.net/admin\n"
                + "API: GET /api/admin/feedbacks?category=%s\n",
                feedback.getId(),
                categoryLabel, category,
                userInfo,
                createdAt,
                feedback.getContent(),
                category
        );

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(new InternetAddress(mailFrom, fromName, "UTF-8"));
            helper.setTo(supportEmail);
            helper.setSubject(subject);
            helper.setText(body);
            mailSender.send(message);
            log.info("Feedback notification sent: id={} category={} → {}",
                    feedback.getId(), category, supportEmail);
        } catch (Exception e) {
            log.error("Failed to send feedback notification: id={} reason={}",
                    feedback.getId(), e.getMessage());
            // 의견 저장 자체는 성공한 상태이므로 예외 던지지 않음
        }
    }
}
