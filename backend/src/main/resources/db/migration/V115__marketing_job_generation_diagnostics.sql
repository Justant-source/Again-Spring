-- Fail-closed video quality generation audit and explicit regeneration lineage.
ALTER TABLE marketing_job
    ADD COLUMN failure_code VARCHAR(64) NULL COMMENT '안정적인 영상 생성/품질 실패 코드',
    ADD COLUMN generation_diagnostics JSON NULL COMMENT '원시 LLM 출력 없는 생성/검증 수치',
    ADD COLUMN actual_duration_ms BIGINT NULL COMMENT '최종 MP4 실제 길이(ms)',
    ADD COLUMN retry_of_job_id BIGINT NULL COMMENT '재생성 원본 marketing_job.id',
    ADD COLUMN generation_attempt INT NOT NULL DEFAULT 1 COMMENT '재생성 계보 내 생성 시도 번호';

CREATE INDEX idx_mj_retry_of_job ON marketing_job (retry_of_job_id);
