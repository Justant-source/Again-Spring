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

    @Transactional
    public void sendCode(String email) {
        if (userRepository.findByEmail(email).isPresent()) {
            throw new BusinessException("USER_ALREADY_EXISTS", "이미 가입된 이메일이에요. 다른 이메일로 인증해 주세요.", 409);
        }

        String code = String.format("%06d", secureRandom.nextInt(1_000_000));

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
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(new InternetAddress(mailFrom, fromName, "UTF-8"));
            helper.setTo(email);
            helper.setSubject("[다시봄] 이메일 인증코드");
            helper.setText(
                "안녕하세요 :)\n\n인증코드: " + code + "\n\n10분 내에 입력해주세요.\n\n"
                + "문의: againspring2026@gmail.com\n다시봄 운영팀 드림"
            );
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
