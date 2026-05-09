-- V19: 일별 집계 통계 테이블 (집계 데이터는 영구 보존)
CREATE TABLE daily_stats (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    stat_date       DATE        NOT NULL COMMENT 'KST 기준 날짜',
    dau             INT         NOT NULL DEFAULT 0 COMMENT '일별 활성 사용자',
    new_users       INT         NOT NULL DEFAULT 0 COMMENT '신규 가입자',
    guest_sessions  INT         NOT NULL DEFAULT 0 COMMENT '게스트 세션 수',
    member_sessions INT         NOT NULL DEFAULT 0 COMMENT '회원 세션 수',
    completed_sessions INT      NOT NULL DEFAULT 0 COMMENT 'finalize 완료 세션',
    avg_turns       DECIMAL(5,2) NOT NULL DEFAULT 0 COMMENT '평균 턴 수',
    crisis_triggers INT         NOT NULL DEFAULT 0 COMMENT '위기 감지 횟수',
    feedback_count  INT         NOT NULL DEFAULT 0 COMMENT '의견 제출 수',
    metadata        JSON        NULL COMMENT '추가 집계 데이터',
    created_at      DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uq_daily_stats_date (stat_date),
    INDEX idx_daily_stats_date (stat_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='일별 서비스 지표 집계';
