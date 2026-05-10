package com.againspring.service;

import com.againspring.common.exception.BusinessException;
import com.againspring.domain.PasswordResetToken;
import com.againspring.domain.User;
import com.againspring.repository.PasswordResetTokenRepository;
import com.againspring.repository.UserRepository;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 비밀번호 재설정 서비스
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PasswordResetService {

    private final PasswordResetTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final JavaMailSender mailSender;
    private final PasswordEncoder passwordEncoder;
    private final Environment environment;

    @Value("${spring.mail.username:againspring2026@gmail.com}")
    private String mailFrom;

    @Value("${app.mail.from-name:다시봄 운영팀}")
    private String fromName;

    @Value("${app.url:https://dev.againspring.net}")
    private String appUrl;

    private static final long TOKEN_EXPIRATION_SECONDS = 1800; // 30분
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final char[] TEMP_PWD_ALPHABET =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789".toCharArray();
    private static final int TEMP_PWD_LENGTH = 12;

    /**
     * 비밀번호 재설정 요청 (임시 비밀번호 방식 — 사용자 요청).
     * 이메일이 존재하면 임시 비밀번호 생성 → 사용자 비번 교체 + must_change_password=true → 메일 발송.
     * 이메일이 없어도 200 응답 (계정 존재 정보 노출 방지).
     */
    @Transactional
    public void requestReset(String email) {
        boolean exists = userRepository.existsByEmail(email);
        if (!exists) {
            log.info("Password reset requested for non-existent email: {}", email);
            return;
        }

        User user = userRepository.findByEmail(email).orElseThrow();
        if (user.getDeletedAt() != null) {
            log.info("Password reset requested for deleted account: {}", email);
            return;
        }

        // 임시 비밀번호 생성 + 즉시 사용자 비번 교체 + 강제 변경 플래그 ON
        String tempPassword = generateTempPassword();
        user.setPasswordHash(passwordEncoder.encode(tempPassword));
        user.setMustChangePassword(true);
        userRepository.save(user);

        // 기존 reset 토큰들 모두 무효화 (혼선 방지)
        tokenRepository.findTopByEmailAndUsedFalseOrderByCreatedAtDesc(email)
                .ifPresent(token -> {
                    token.setUsed(true);
                    tokenRepository.save(token);
                });

        sendTempPasswordEmail(email, tempPassword);
        log.info("Temporary password issued for user: {}", user.getId());
    }

    private String generateTempPassword() {
        StringBuilder sb = new StringBuilder(TEMP_PWD_LENGTH);
        for (int i = 0; i < TEMP_PWD_LENGTH; i++) {
            sb.append(TEMP_PWD_ALPHABET[RANDOM.nextInt(TEMP_PWD_ALPHABET.length)]);
        }
        return sb.toString();
    }

    /**
     * 비밀번호 재설정
     * 토큰 검증 후 비밀번호 변경
     */
    @Transactional
    public void resetPassword(String tokenValue, String newPassword) {
        Instant now = Instant.now();

        PasswordResetToken token = tokenRepository
                .findByTokenAndUsedFalseAndExpiresAtAfter(tokenValue, now)
                .orElseThrow(() -> new BusinessException(
                        "INVALID_RESET_TOKEN",
                        "비밀번호 재설정 링크가 유효하지 않거나 만료되었어요"
                ));

        // 사용자 조회
        User user = userRepository.findByEmail(token.getEmail())
                .orElseThrow(() -> new BusinessException(
                        "USER_NOT_FOUND",
                        "사용자를 찾을 수 없어요"
                ));

        // 비밀번호 갱신
        String encodedPassword = passwordEncoder.encode(newPassword);
        user.setPasswordHash(encodedPassword);
        userRepository.save(user);

        // 토큰 사용 처리
        token.setUsed(true);
        tokenRepository.save(token);

        log.info("Password reset successful for user: {}", user.getId());
    }

    /**
     * 임시 비밀번호 이메일 발송.
     * dev에서 발송 실패 시 임시 비밀번호를 로그에 출력해 직접 사용 가능.
     */
    private void sendTempPasswordEmail(String email, String tempPassword) {
        boolean isDev = java.util.Arrays.asList(environment.getActiveProfiles()).contains("dev");

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(new InternetAddress(mailFrom, fromName, "UTF-8"));
            helper.setTo(email);
            helper.setSubject("[다시봄] 임시 비밀번호 발급");
            helper.setText(
                    "안녕하세요 :)\n\n" +
                    "비밀번호 재설정을 요청하셔서 임시 비밀번호를 발급해 드렸어요.\n\n" +
                    "─────────────────\n" +
                    "  임시 비밀번호: " + tempPassword + "\n" +
                    "─────────────────\n\n" +
                    "위 임시 비밀번호로 로그인하시면 새 비밀번호를 바로 설정하실 수 있어요.\n" +
                    "보안을 위해 로그인 직후 반드시 비밀번호를 변경해 주세요.\n\n" +
                    "본인이 요청하지 않으셨다면 운영팀으로 알려주시거나,\n" +
                    "임시 비밀번호로 로그인 후 즉시 새 비밀번호로 바꿔주세요.\n\n" +
                    "문의: againspring2026@gmail.com\n다시봄 운영팀 드림"
            );
            mailSender.send(message);
            log.info("Temporary password email sent to {}", email);
        } catch (Exception e) {
            log.error("Failed to send temp password email to {}: {}", email, e.getMessage());
            if (isDev) {
                log.warn(">>> [DEV] 이메일 발송 실패 — 임시 비밀번호: {}", tempPassword);
                return;
            }
            throw new BusinessException("EMAIL_SEND_FAILED", "이메일 발송에 실패했어요. 잠시 후 다시 시도해주세요.");
        }
    }
}
