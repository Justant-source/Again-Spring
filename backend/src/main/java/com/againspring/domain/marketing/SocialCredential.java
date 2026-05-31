package com.againspring.domain.marketing;

import java.time.Instant;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * 소셜 플랫폼 인증 자격증 엔티티 (암호화 저장)
 * X, Instagram 등 플랫폼별 사용자명/비밀번호 저장
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "social_credentials")
@EntityListeners(AuditingEntityListener.class)
@Slf4j
public class SocialCredential {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String platform;

    @Column(name = "email_enc", nullable = false, columnDefinition = "TEXT")
    private String emailEnc;

    @Column(name = "password_enc", nullable = false, columnDefinition = "TEXT")
    private String passwordEnc;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;
}
