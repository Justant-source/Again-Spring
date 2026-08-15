-- Additive AS mirror of ASM/Waggle structured terminal-failure contract.
ALTER TABLE marketing_job
    ADD COLUMN failure_stage VARCHAR(64) NULL COMMENT '생성 실패가 난 파이프라인 단계',
    ADD COLUMN retryable BOOLEAN NULL COMMENT '수동 재생성의 유효성',
    ADD COLUMN error_summary VARCHAR(1000) NULL COMMENT '정제된 운영자용 실패 요약';
