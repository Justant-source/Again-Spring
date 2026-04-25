package com.againspring.service;

import com.againspring.common.exception.BusinessException;
import com.againspring.domain.PasswordResetToken;
import com.againspring.domain.User;
import com.againspring.repository.PasswordResetTokenRepository;
import com.againspring.repository.UserRepository;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
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

    @Value("${spring.mail.username:}")
    private String mailFrom;

    @Value("${app.url:https://dev.againspring.net}")
    private String appUrl;

    private static final long TOKEN_EXPIRATION_SECONDS = 1800; // 30분

    /**
     * 비밀번호 재설정 요청
     * 이메일이 존재하면 토큰 생성 및 이메일 발송
     * 이메일이 없어도 200 응답 (계정 존재 정보 노출 방지)
     */
    @Transactional
    public void requestReset(String email) {
        // 이메일 존재 확인
        boolean exists = userRepository.existsByEmail(email);

        if (!exists) {
            log.info("Password reset requested for non-existent email: {}", email);
            return; // 실패해도 성공과 동일하게 응답
        }

        // 기존 미사용 토큰 무효화
        tokenRepository.findTopByEmailAndUsedFalseOrderByCreatedAtDesc(email)
                .ifPresent(token -> {
                    token.setUsed(true);
                    tokenRepository.save(token);
                });

        // 새 토큰 생성
        String tokenValue = UUID.randomUUID().toString().replace("-", "");
        Instant expiresAt = Instant.now().plusSeconds(TOKEN_EXPIRATION_SECONDS);

        PasswordResetToken token = PasswordResetToken.builder()
                .email(email)
                .token(tokenValue)
                .expiresAt(expiresAt)
                .used(false)
                .build();

        tokenRepository.save(token);

        // 이메일 발송
        sendResetEmail(email, tokenValue);
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
     * 비밀번호 재설정 이메일 발송
     */
    private void sendResetEmail(String email, String tokenValue) {
        String resetLink = appUrl + "/reset-password/" + tokenValue;
        boolean isDev = java.util.Arrays.asList(environment.getActiveProfiles()).contains("dev");

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            if (mailFrom != null && !mailFrom.isBlank()) {
                message.setFrom(mailFrom);
            }
            message.setTo(email);
            message.setSubject("[다시봄] 비밀번호 재설정");
            message.setText(
                    "안녕하세요 :)\n\n" +
                    "비밀번호를 재설정하시려면 아래 링크를 클릭해주세요.\n\n" +
                    resetLink + "\n\n" +
                    "이 링크는 30분 동안 유효합니다.\n\n" +
                    "본인이 요청하지 않으셨다면 이 이메일을 무시해주세요.\n\n" +
                    "다시봄 팀 드림"
            );
            mailSender.send(message);
            log.info("Password reset email sent to {}", email);
        } catch (Exception e) {
            log.error("Failed to send password reset email to {}: {}", email, e.getMessage());
            if (isDev) {
                log.warn(">>> [DEV] 이메일 발송 실패 — 아래 링크를 직접 사용하세요: {}", resetLink);
                return;
            }
            throw new BusinessException("EMAIL_SEND_FAILED", "이메일 발송에 실패했어요. 잠시 후 다시 시도해주세요.");
        }
    }
}
