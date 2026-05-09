package com.againspring.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;
import io.hypersistence.utils.hibernate.type.json.JsonType;

import java.time.Instant;
import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "feedbacks")
public class Feedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 32)
    private String userId;

    @Column(length = 32)
    private String sessionId;

    @Column(length = 50, nullable = false)
    private String category;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(nullable = false)
    @Builder.Default
    private Boolean contactConsent = false;

    @Column(length = 255)
    private String contactEmail;

    @Column(length = 500)
    private String pageUrl;

    @Column(length = 500)
    private String userAgent;

    @Type(JsonType.class)
    @Column(columnDefinition = "JSON")
    private Map<String, Object> metadata;

    @Column(length = 20, nullable = false)
    @Builder.Default
    private String status = "pending";

    @Column(columnDefinition = "TEXT")
    private String adminNote;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
