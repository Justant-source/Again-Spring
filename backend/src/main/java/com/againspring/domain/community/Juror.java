package com.againspring.domain.community;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * 배심원 (V17 커뮤니티)
 * 포스트에 대해 AI 또는 사용자가 투표하는 배심원
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "jurors")
@EntityListeners(AuditingEntityListener.class)
public class Juror {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 32)
    private String postId;

    @Type(JsonType.class)
    @Column(columnDefinition = "JSON")
    private JurorPersona persona;

    @Column(name = "chosen_option_id")
    private Long chosenOptionId;

    @Column(columnDefinition = "TEXT")
    private String empathyComment;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * 배심원 페르소나 (JSON 저장용)
     */
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class JurorPersona {
        private String ageGroup;
        private String gender;
        private String disposition;
        private String valueOrientation;
    }
}
