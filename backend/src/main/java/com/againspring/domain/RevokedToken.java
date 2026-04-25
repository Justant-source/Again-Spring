package com.againspring.domain;

import java.time.Instant;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * 취소된 JWT 토큰 (블랙리스트)
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "revoked_tokens")
@EntityListeners(AuditingEntityListener.class)
public class RevokedToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 64, nullable = false, unique = true)
    private String jti;

    @Column(length = 64)
    private String userId;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant revokedAt;

    @Column(nullable = false)
    private Instant expiresAt;
}
