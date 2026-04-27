package com.againspring.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Phase D — QuestionPrioritizer 가중치 외부화.
 * application.yml: app.phase-d.priority.* 로 재배포 없이 조정.
 *
 * 권위본: backend/docs/implementation/phase-d-implementation-instructions.md §6.3
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.phase-d.priority")
public class PhaseDProperties {
    private double recencyWeight = 0.5;
    private double urgencyWeight = 0.3;
    private double coverageGapWeight = 0.2;
    // state/category multiplier는 정책 회의 후 코드에서 변경
}
