package com.againspring.domain;

import java.time.Instant;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "guest_sessions", indexes = {
    @Index(name = "idx_guest_sessions_invite_token", columnList = "invite_token"),
    @Index(name = "idx_guest_sessions_guest_id", columnList = "guest_id")
})
public class GuestSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "invite_token", length = 64, nullable = false)
    private String inviteToken;

    @Column(name = "guest_id", length = 32, nullable = false)
    private String guestId;

    @Column(name = "guest_nickname", length = 100)
    private String guestNickname;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
}
