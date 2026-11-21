package com.againspring.domain.marketing;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/** 포맷 arm 정의(V132). 영상 매개변수 묶음. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "marketing_format_arm")
public class MarketingFormatArmEntity {

    @Id
    @Column(name = "arm_key", length = 32)
    private String armKey;

    @Column(name = "render_profile", nullable = false, length = 32)
    private String renderProfile;

    @Column(name = "script_style", nullable = false, length = 16)
    private String scriptStyle;

    @Column(name = "comment_count", nullable = false)
    private int commentCount;

    @Column(name = "outro_mode", nullable = false, length = 16)
    private String outroMode;

    @Column(name = "max_duration_sec")
    private Integer maxDurationSec;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "description", length = 200)
    private String description;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
