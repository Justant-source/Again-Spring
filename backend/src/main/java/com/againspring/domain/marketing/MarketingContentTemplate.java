package com.againspring.domain.marketing;

import java.time.Instant;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "marketing_content_templates")
@EntityListeners(AuditingEntityListener.class)
public class MarketingContentTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MarketingContent.Platform platform;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "body_template", columnDefinition = "MEDIUMTEXT", nullable = false)
    private String bodyTemplate;

    @Column(name = "variables_json", columnDefinition = "JSON")
    private String variablesJson;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "created_by")
    private Long createdBy;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;
}
