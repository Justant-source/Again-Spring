package com.againspring.integration;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MariaDBContainer;

/**
 * Testcontainers MariaDB + 실 Flyway 기반 통합테스트 베이스.
 *
 * - application-integration.yml 프로파일 적용 (MariaDB 드라이버, Flyway enabled)
 * - 정적 컨테이너: JVM 당 1회 기동, 동일 클래스의 모든 테스트가 공유
 * - V1~V24 불변 규약: 마이그레이션 수정이 필요하면 항상 새 V(n+1) 파일로만 추가
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("integration")
public abstract class MariaDbIntegrationSupport {

    @SuppressWarnings({"resource", "rawtypes"})
    static final MariaDBContainer DB = new MariaDBContainer<>("mariadb:11")
            .withDatabaseName("againspring_test")
            .withUsername("test")
            .withPassword("test")
            // V7+ 마이그레이션이 utf8mb4_unicode_ci COLLATE를 사용하므로 서버 기본값 통일
            .withCommand("--character-set-server=utf8mb4",
                         "--collation-server=utf8mb4_unicode_ci");

    static {
        DB.start();
    }

    @DynamicPropertySource
    static void dataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",
                () -> DB.getJdbcUrl() + "?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC");
        registry.add("spring.datasource.username", DB::getUsername);
        registry.add("spring.datasource.password", DB::getPassword);
    }
}
