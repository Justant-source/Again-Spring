package com.againspring.integration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Flyway V1~V32이 실 MariaDB(Testcontainers) 위에서 전부 성공하는지 검증.
 * 새 마이그레이션 추가 시 hasSize 값도 함께 갱신할 것.
 */
class FlywayMigrationIT extends MariaDbIntegrationSupport {

    @Autowired
    private Flyway flyway;

    @Autowired
    private DataSource dataSource;

    @Test
    void allMigrationsApplied() {
        MigrationInfo[] applied = flyway.info().applied();
        long failedCount = java.util.Arrays.stream(applied)
                .filter(m -> m.getState() == MigrationState.FAILED)
                .count();
        int pendingCount = flyway.info().pending().length;

        assertThat(failedCount).as("실패한 마이그레이션").isZero();
        assertThat(pendingCount).as("미적용 마이그레이션").isZero();
        // V58 추가됨 — V1~V47(기존) + V48~V58(V17 커뮤니티) = 총 58개
        // 주의: 마이그레이션 추가 시 이 숫자도 함께 갱신할 것
        assertThat(applied).as("전체 적용 마이그레이션 수").hasSize(58);
    }

    @Test
    void v24TutorialColumnExists() throws SQLException {
        assertThat(columnExists("users", "tutorial_completed_at"))
                .as("V24: users.tutorial_completed_at 컬럼 존재").isTrue();
    }

    @Test
    void v24TutorialColumnExistsOnUsers() throws SQLException {
        // V56에서 legacy mediation tables(messages 등) 삭제 — messages 컬럼 검사는 제거됨
        // V24: users.tutorial_completed_at 은 users 테이블에 있으므로 유효
        assertThat(columnExists("users", "tutorial_completed_at"))
                .as("V24: users.tutorial_completed_at 컬럼 존재").isTrue();
    }

    private boolean columnExists(String table, String column) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             ResultSet rs = conn.getMetaData().getColumns(null, null, table, column)) {
            return rs.next();
        }
    }
}
