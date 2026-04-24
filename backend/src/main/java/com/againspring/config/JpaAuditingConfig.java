package com.againspring.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * JPA 감시 설정
 * 엔티티의 생성/수정 시간 자동 기록
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
