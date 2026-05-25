-- V32: 마케팅 시뮬레이션 비용 로그 테이블 추가
-- 각 시뮬레이션 실행 시 LLM 토큰 사용량, 모델, 비용 기록
CREATE TABLE IF NOT EXISTS marketing_usage_logs (
    id            BIGINT NOT NULL AUTO_INCREMENT,
    simulation_id BIGINT,
    model         VARCHAR(100) NOT NULL COMMENT 'LLM 모델 명 (e.g. claude-haiku-4-5)',
    input_tokens  INT NOT NULL DEFAULT 0 COMMENT '입력 토큰 수',
    output_tokens INT NOT NULL DEFAULT 0 COMMENT '출력 토큰 수',
    cost_usd      DECIMAL(8, 4) NOT NULL DEFAULT 0.0000 COMMENT 'USD 비용',
    created_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '기록 생성 시각',
    PRIMARY KEY (id),
    INDEX idx_mul_simulation_id (simulation_id),
    INDEX idx_mul_created_at (created_at DESC),
    CONSTRAINT fk_mul_simulation FOREIGN KEY (simulation_id)
        REFERENCES marketing_simulations(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='마케팅 시뮬레이션 LLM 사용 로그';
