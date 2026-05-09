package com.againspring.service;

import com.againspring.repository.EmailVerificationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
@DisplayName("Email Sender Tests — 발신자 설정 검증")
class EmailServiceSenderTest {

    @Mock JavaMailSender mailSender;
    @Mock EmailVerificationRepository verificationRepository;
    @Mock Environment environment;

    @Test
    @DisplayName("EmailVerificationService mailFrom 기본값이 againspring2026@gmail.com")
    void verificationServiceMailFromDefault() {
        EmailVerificationService svc = new EmailVerificationService(verificationRepository, mailSender, environment);
        ReflectionTestUtils.setField(svc, "mailFrom", "againspring2026@gmail.com");

        String mailFrom = (String) ReflectionTestUtils.getField(svc, "mailFrom");
        assertThat(mailFrom).isEqualTo("againspring2026@gmail.com");
        assertThat(mailFrom).doesNotContain("newdream1501");
    }

    @Test
    @DisplayName("EmailVerificationService fromName이 '다시봄 운영팀'")
    void verificationServiceFromName() {
        EmailVerificationService svc = new EmailVerificationService(verificationRepository, mailSender, environment);
        ReflectionTestUtils.setField(svc, "fromName", "다시봄 운영팀");

        String fromName = (String) ReflectionTestUtils.getField(svc, "fromName");
        assertThat(fromName).isEqualTo("다시봄 운영팀");
    }

    @Test
    @DisplayName("PasswordResetService mailFrom 기본값이 againspring2026@gmail.com")
    void passwordResetServiceMailFromDefault() {
        PasswordResetService svc = new PasswordResetService(
            mock(com.againspring.repository.PasswordResetTokenRepository.class),
            mock(com.againspring.repository.UserRepository.class),
            mailSender,
            mock(org.springframework.security.crypto.password.PasswordEncoder.class),
            environment
        );
        ReflectionTestUtils.setField(svc, "mailFrom", "againspring2026@gmail.com");

        String mailFrom = (String) ReflectionTestUtils.getField(svc, "mailFrom");
        assertThat(mailFrom).isEqualTo("againspring2026@gmail.com");
        assertThat(mailFrom).doesNotContain("newdream1501");
    }

    @Test
    @DisplayName("PasswordResetService fromName이 '다시봄 운영팀'")
    void passwordResetServiceFromName() {
        PasswordResetService svc = new PasswordResetService(
            mock(com.againspring.repository.PasswordResetTokenRepository.class),
            mock(com.againspring.repository.UserRepository.class),
            mailSender,
            mock(org.springframework.security.crypto.password.PasswordEncoder.class),
            environment
        );
        ReflectionTestUtils.setField(svc, "fromName", "다시봄 운영팀");

        String fromName = (String) ReflectionTestUtils.getField(svc, "fromName");
        assertThat(fromName).isEqualTo("다시봄 운영팀");
    }
}
