package com.againspring.domain.marketing;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;

/** 계정 지표 일일 스냅샷(V132). (platform, collected_on)당 1행. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "marketing_account_stats")
public class MarketingAccountStats {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "platform", nullable = false, length = 40)
    private String platform;

    @Column(name = "collected_on", nullable = false)
    private LocalDate collectedOn;

    @Column(name = "followers")
    private Long followers;

    @Column(name = "extra_json", columnDefinition = "LONGTEXT")
    private String extraJson;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;
}
