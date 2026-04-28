package com.againspring.domain;

import com.againspring.domain.enums.MessageSender;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

/**
 * 메시지 엔티티 (V1.5 카톡식 채팅)
 * 사용자 메시지 + 중재자 응답을 모두 저장
 */
@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
@Entity @Table(name = "messages")
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false, length = 36)
    private String sessionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private MessageSender sender;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(name = "char_count", nullable = false)
    private Integer charCount;

    @Column(name = "is_finalize_suggestion", nullable = false)
    @Builder.Default
    private Boolean isFinalizeSuggestion = false;

    @Column(name = "is_partner_join_notice", nullable = false)
    @Builder.Default
    private Boolean isPartnerJoinNotice = false;

    @Column(name = "crisis_level")
    private Integer crisisLevel;

    @Column(name = "llm_model", length = 64)
    private String llmModel;

    @Column(name = "tokens_used")
    @Builder.Default
    private Integer tokensUsed = 0;

    @Column(name = "llm_latency_ms")
    @Builder.Default
    private Long llmLatencyMs = 0L;

    @Column(name = "dismissed_at")
    private Instant dismissedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
        if (charCount == null) charCount = content != null ? content.length() : 0;
    }
}
