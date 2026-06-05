package com.againspring.service;

import com.againspring.common.exception.BusinessException;
import com.againspring.domain.EmailVerification;
import com.againspring.repository.EmailVerificationRepository;
import com.againspring.repository.UserRepository;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.security.SecureRandom;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailVerificationService {

    private final EmailVerificationRepository repository;
    private final UserRepository userRepository;
    private final JavaMailSender mailSender;
    private final Environment environment;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${spring.mail.username:againspring2026@gmail.com}")
    private String mailFrom;

    @Value("${app.mail.from-name:다시봄 운영팀}")
    private String fromName;

    @Value("${app.url:https://dev.againspring.net}")
    private String appUrl;

    @Transactional
    public void sendCode(String email) {
        if (userRepository.findByEmail(email).isPresent()) {
            throw new BusinessException("USER_ALREADY_EXISTS", "이미 가입된 이메일이에요. 다른 이메일로 인증해 주세요.", 409);
        }

        String code = String.format("%04d", secureRandom.nextInt(10_000));

        EmailVerification ev = EmailVerification.builder()
                .email(email)
                .code(code)
                .expiresAt(Instant.now().plusSeconds(600))
                .used(false)
                .build();
        repository.save(ev);

        boolean isDev = java.util.Arrays.asList(environment.getActiveProfiles()).contains("dev");

        try {
            MimeMessage message = mailSender.createMimeMessage();
            // multipart=true → 평문 + HTML 대체본 동시 제공 (HTML 미지원 클라이언트 폴백)
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(new InternetAddress(mailFrom, fromName, "UTF-8"));
            helper.setTo(email);
            helper.setSubject("[다시봄] 인증번호 " + code);
            helper.setText(buildPlainText(code, email), buildHtml(code, email));
            mailSender.send(message);
            log.info("Verification code sent to {}", email);
        } catch (Exception e) {
            log.error("Failed to send verification email to {}: {}", email, e.getMessage());
            if (isDev) {
                log.warn(">>> [DEV] 이메일 발송 실패 — 아래 코드를 직접 사용하세요: {} → {}", email, code);
                return;
            }
            throw new BusinessException("EMAIL_SEND_FAILED", "이메일 발송에 실패했어요. 잠시 후 다시 시도해주세요.");
        }
    }

    /** 딥링크 URL — 가입 페이지에 이메일·코드 자동 입력 */
    private String verifyUrl(String code, String email) {
        String enc = java.net.URLEncoder.encode(email, java.nio.charset.StandardCharsets.UTF_8);
        return appUrl + "/signup?email=" + enc + "&code=" + code;
    }

    /** HTML 미지원 클라이언트용 평문 폴백 */
    private String buildPlainText(String code, String email) {
        return "다시봄 이메일 인증\n\n인증번호: " + code + "\n\n"
                + "아래 링크를 열면 인증번호가 자동으로 입력돼요:\n" + verifyUrl(code, email) + "\n\n"
                + "10분 안에 입력해 주세요.\n"
                + "본인이 요청하지 않았다면 이 메일을 무시하세요.\n\n"
                + "문의: againspring2026@gmail.com\n다시봄 운영팀 드림";
    }

    /**
     * 다시봄 look&feel HTML 인증 메일.
     *
     * 이메일 클라이언트는 JavaScript를 모두 차단하므로 onclick/clipboard API는 동작하지 않음.
     * 대신 딥링크(일반 <a href>) 버튼을 사용 — 클릭 한 번으로 가입 페이지가 코드 자동입력된 채 열림.
     * 코드 박스에 user-select:all 적용 — 탭/클릭 한 번으로 4자리 전체 선택 후 복사 가능.
     */
    private String buildHtml(String code, String email) {
        String url = verifyUrl(code, email);
        return "<!DOCTYPE html>"
            + "<html lang=\"ko\"><head><meta charset=\"UTF-8\">"
            + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">"
            + "<title>다시봄 인증번호</title></head>"
            + "<body style=\"margin:0; padding:0; background:#FBF7F2;\">"
            + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" "
            +   "style=\"background:#FBF7F2; padding:32px 16px;\"><tr><td align=\"center\">"
            + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" "
            +   "style=\"max-width:440px; background:#ffffff; border-radius:18px; "
            +   "box-shadow:0 6px 24px rgba(120,90,60,0.08); overflow:hidden;\">"
            + "<tr><td style=\"padding:40px 32px 36px; text-align:center; "
            +   "font-family:-apple-system,BlinkMacSystemFont,'Apple SD Gothic Neo','Malgun Gothic',sans-serif;\">"
            // 로고
            + "<div style=\"font-family:Georgia,'Times New Roman',serif; font-size:24px; "
            +   "font-weight:600; color:#C9785A; letter-spacing:0.02em;\">다시봄</div>"
            + "<div style=\"width:28px; height:3px; background:#5F8F76; border-radius:2px; margin:12px auto 0;\"></div>"
            // 안내 문구
            + "<p style=\"font-size:15px; color:#5b5048; line-height:1.75; margin:26px 0 22px; font-weight:500;\">"
            +   "이메일 인증번호예요</p>"
            // 코드 박스 — user-select:all로 탭 한 번에 전체 선택
            + "<div style=\"background:#FBF3EC; border:1px solid #EBD9CB; border-radius:14px; "
            +   "padding:24px 0 22px; margin:0 auto 22px;\">"
            + "<span style=\"display:inline-block; font-family:'Courier New',Courier,monospace; "
            +   "font-size:48px; font-weight:700; color:#C9785A; letter-spacing:0.36em; "
            +   "padding-left:0.36em; line-height:1; "
            +   "-webkit-user-select:all; -moz-user-select:all; user-select:all; cursor:text;\">"
            +   code + "</span></div>"
            // 딥링크 버튼 — 일반 <a href>, JS 불필요, 모든 이메일 클라이언트에서 동작
            + "<a href=\"" + url + "\" "
            +   "style=\"display:inline-block; background:#C9785A; color:#ffffff; text-decoration:none; "
            +   "font-size:15px; font-weight:600; padding:14px 36px; border-radius:11px; "
            +   "letter-spacing:0.01em; box-shadow:0 3px 10px rgba(201,120,90,0.28);\">인증번호 자동입력하기</a>"
            + "<p style=\"font-size:12px; color:#bcb0a2; margin:16px 0 0; line-height:1.7;\">"
            +   "버튼을 누르면 가입 화면에 번호가 채워져요<br>10분 안에 입력해 주세요</p>"
            // 푸터
            + "<div style=\"border-top:1px solid #f0e8df; margin-top:28px; padding-top:20px; "
            +   "font-size:11px; color:#b3a89b; line-height:1.7;\">"
            +   "본인이 요청하지 않았다면 이 메일을 무시하셔도 괜찮아요.<br>"
            +   "문의 · <a href=\"mailto:againspring2026@gmail.com\" "
            +     "style=\"color:#5F8F76; text-decoration:none;\">againspring2026@gmail.com</a><br>"
            +   "<span style=\"color:#c8bdb0;\">다시봄 운영팀 드림</span></div>"
            + "</td></tr></table></td></tr></table></body></html>";
    }

    @Transactional
    public void verifyCode(String email, String code) {
        EmailVerification ev = repository
                .findTopByEmailAndUsedFalseAndExpiresAtAfterOrderByCreatedAtDesc(email, Instant.now())
                .orElseThrow(() -> new BusinessException("EMAIL_CODE_EXPIRED", "인증코드가 만료되었거나 존재하지 않아요"));

        if (!ev.getCode().equals(code)) {
            throw new BusinessException("EMAIL_CODE_INVALID", "인증코드가 올바르지 않아요");
        }

        ev.markUsed();
        repository.save(ev);
    }
}
