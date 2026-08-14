-- Local reconciliation metadata for delayed ASM/WaggleBot rendering.
-- A transient renderer poll timeout must remain observable without becoming a terminal publish failure.
ALTER TABLE marketing_job
    ADD COLUMN remote_status VARCHAR(32) NULL COMMENT 'ASM이 마지막으로 보고한 상태',
    ADD COLUMN remote_phase VARCHAR(128) NULL COMMENT 'ASM이 마지막으로 보고한 단계',
    ADD COLUMN processing_detail TEXT NULL COMMENT '일시적 원격 처리 지연 상세',
    ADD COLUMN waiting_external_since DATETIME(6) NULL COMMENT '원격 완료 대기 시작 시각',
    ADD COLUMN sla_breached_at DATETIME(6) NULL COMMENT '생성 SLA 초과 시각';

CREATE INDEX idx_mj_remote_reconciliation ON marketing_job (status, waiting_external_since);
