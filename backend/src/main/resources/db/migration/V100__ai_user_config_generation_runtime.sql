-- V100: AI 생성 런타임 설정 (타임아웃·새벽 배치) — /admin/ai-user SSOT
-- Doc-Sync: docs/shared/api/database-schema.md · docs/ai-user/operations.md · docs/shared/api/admin.md

ALTER TABLE ai_user_generation_config
    ADD COLUMN bundle_timeout_ms INT NOT NULL DEFAULT 600000
        COMMENT '구조화 LLM 생성 타임아웃(ms). solo/paired/human-reply 공통. 저장 즉시 반영',
    ADD COLUMN nightly_paired_share DECIMAL(4,3) NOT NULL DEFAULT 0.200
        COMMENT '새벽 배치 양면(paired) 비율. ceil(target_posts * share)',
    ADD COLUMN nightly_slot_from_hour TINYINT NOT NULL DEFAULT 8
        COMMENT '새벽 배치 발행 슬롯 샘플 시작(KST hour)',
    ADD COLUMN nightly_slot_to_hour TINYINT NOT NULL DEFAULT 22
        COMMENT '새벽 배치 발행 슬롯 샘플 끝(KST hour)',
    ADD COLUMN nightly_slot_min_spacing_minutes SMALLINT NOT NULL DEFAULT 45
        COMMENT '새벽 배치 글 슬롯 최소 간격(분)';