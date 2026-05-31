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
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * 소셜 플랫폼 세션 엔티티 (Playwright storageState 암호화 저장)
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "social_sessions")
@EntityListeners(AuditingEntityListener.class)
@Slf4j
public class SocialSession {

    public enum SessionStatus {
        SEEDED, EXPIRED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String platform;

    @Column(name = "storage_state_enc", nullable = false, columnDefinition = "MEDIUMTEXT")
    private String storageStateEnc;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private SessionStatus status = SessionStatus.SEEDED;

    @CreatedDate
    @Column(name = "seeded_at", nullable = false, updatable = false)
    private Instant seededAt;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;
}
