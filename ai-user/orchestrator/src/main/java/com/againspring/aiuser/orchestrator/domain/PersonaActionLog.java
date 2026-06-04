package com.againspring.aiuser.orchestrator.domain;

import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;

import java.time.Instant;
import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "persona_action_log")
public class PersonaActionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "persona_id", length = 32, nullable = false)
    private String personaId;

    @Column(name = "action_type", length = 16, nullable = false)
    private String actionType;  // LIKE/VOTE/COMMENT/REPLY/POST/INVITE_ANSWER

    @Column(name = "target_type", length = 16)
    private String targetType;  // POST or COMMENT

    @Column(name = "target_id", length = 64)
    private String targetId;   // VARCHAR(64) to hold both VARCHAR(32) post ids and BIGINT comment ids

    @Column(name = "used_llm", nullable = false)
    @Builder.Default
    private boolean usedLlm = false;

    @Column(length = 16, nullable = false)
    @Builder.Default
    private String status = "POSTED";  // PLANNED/GENERATING/POSTED/FAILED/BLOCKED

    @Column(name = "correlation_id", length = 64)
    private String correlationId;

    @Type(JsonType.class)
    @Column(columnDefinition = "JSON")
    private Map<String, Object> detail;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
