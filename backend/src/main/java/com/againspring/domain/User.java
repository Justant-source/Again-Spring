package com.againspring.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Type;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import io.hypersistence.utils.hibernate.type.json.JsonType;

/**
 * 사용자 엔티티 (MariaDB JPA)
 * 게스트 모드 포함
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "users")
@EntityListeners(AuditingEntityListener.class)
public class User {

    @Id
    @Column(length = 32)
    private String id;

    @Column(length = 255, unique = true)
    private String email;

    @Column(length = 255)
    private String passwordHash;

    @Column(length = 50)
    private String provider;

    @Column(name = "provider_id", length = 255)
    private String providerId;

    @Column(length = 100, nullable = false)
    private String nickname;

    @Column(length = 50)
    private String communicationStyle;

    @Column(name = "mbti_type", length = 8)
    private String mbtiType;

    @Type(JsonType.class)
    @Column(columnDefinition = "JSON")
    private List<Integer> onboardingAnswers;

    @Type(JsonType.class)
    @Column(name = "mbti_profile", columnDefinition = "JSON")
    private java.util.Map<String, Integer> mbtiProfile;

    @Type(JsonType.class)
    @Column(columnDefinition = "JSON")
    @Builder.Default
    private List<String> roles = new ArrayList<>();

    @Column
    private Instant deletedAt;

    @Column(name = "is_guest", nullable = false)
    @Builder.Default
    private boolean isGuest = false;

    /** 임시 비밀번호 발급 후 강제 변경 필요 여부 (V20) */
    @Column(name = "must_change_password", nullable = false)
    @Builder.Default
    private boolean mustChangePassword = false;

    @Column(name = "onboarding_completed_at")
    private Instant onboardingCompletedAt;

    /** V24: 30초 튜토리얼 완료 시각. NULL이면 미완료. */
    @Column(name = "tutorial_completed_at")
    private Instant tutorialCompletedAt;

    /** V22: 사용자별 중재자 톤 기본값 X (0=팩트, 100=공감). NULL이면 communicationStyle 매핑 fallback. */
    @Column(name = "mediator_default_x")
    private Integer mediatorDefaultX;

    @Column(name = "terms_agreed_at")
    private Instant termsAgreedAt;

    @Column(name = "privacy_agreed_at")
    private Instant privacyAgreedAt;

    @Column(name = "disclaimer_agreed_at")
    private Instant disclaimerAgreedAt;

    @Column(name = "marketing_agreed_at")
    private Instant marketingAgreedAt;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;
}
