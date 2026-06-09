package com.againspring.config;

import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * prod 프로파일에서 Flyway 기동 시 failed 마이그레이션 항목을 자동으로 repair한 뒤 migrate.
 * repair()는 멱등 — 클린한 스키마 히스토리에서 실행해도 무해.
 * V79 드롭 마이그레이션 FK 오류로 인한 failed 상태 복구용으로 도입(2026-06-09).
 */
@Configuration
@Profile("prod")
public class FlywayRepairConfig {

    private static final Logger log = LoggerFactory.getLogger(FlywayRepairConfig.class);

    @Bean
    public FlywayMigrationStrategy repairThenMigrate() {
        return flyway -> {
            log.info("[Flyway] repair() 실행 — failed 항목 제거 후 migrate");
            flyway.repair();
            flyway.migrate();
        };
    }
}
