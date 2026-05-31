package com.againspring.domain.community;

import com.againspring.domain.enums.ThreeWayRole;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * 3자 중재 세션 메시지 (V17 커뮤니티)
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "three_way_messages")
@EntityListeners(AuditingEntityListener.class)
public class ThreeWayMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tws_id", nullable = false, length = 32)
    private String twsId;

    @Enumerated(EnumType.STRING)
    @Column(name = "author_role", nullable = false, length = 20)
    private ThreeWayRole authorRole;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "complete";

    @Column(name = "llm_model", length = 80)
    private String llmModel;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}
