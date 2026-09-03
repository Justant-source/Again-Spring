-- V21__create_ai_provider_snapshot.sql
-- nightly-ai-user-batch.sh가 provider_*를 임시로 CLAUDE로 켜기 전 원래 값을 보관한다.
-- 스크립트가 SIGKILL로 죽어 trap 복원이 안 돼도 orchestrator NightlyProviderStaleReconciler가 여기서 복원한다.
CREATE TABLE IF NOT EXISTS ai_provider_snapshot (
    id                         INT          NOT NULL DEFAULT 1,
    provider_ai_post_bundle    VARCHAR(16)  NOT NULL DEFAULT 'OFF',
    provider_human_post_plan   VARCHAR(16)  NOT NULL DEFAULT 'OFF',
    provider_human_interaction VARCHAR(16)  NOT NULL DEFAULT 'OFF',
    taken_at                   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    taken_by                   VARCHAR(64)  NOT NULL DEFAULT 'nightly-batch',
    restored_at                DATETIME(3)  NULL,
    PRIMARY KEY (id),
    CONSTRAINT chk_provider_snapshot_singleton CHECK (id = 1)
);
