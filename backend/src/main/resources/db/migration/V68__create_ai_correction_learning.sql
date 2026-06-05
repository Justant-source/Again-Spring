-- V68: AI 첨삭 학습 기능 테이블 생성
-- 첨삭 원천 기록 + 전역 금지 규칙

-- 첨삭 원천 기록 (감사 + 재현 + example_bank 환류 추적)
CREATE TABLE ai_content_corrections (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    target_type     VARCHAR(16)  NOT NULL COMMENT 'POST | COMMENT',
    target_id       VARCHAR(64)  NOT NULL COMMENT 'post.id(VARCHAR32) 또는 comment.id(BIGINT) 문자열화',
    persona_id      VARCHAR(32)  NOT NULL COMMENT '= users.id = personas.id',
    category        VARCHAR(50)  NULL     COMMENT '글 카테고리 (example_bank 환류 시 사용)',
    original_text   LONGTEXT     NOT NULL COMMENT '첨삭 전 본문',
    corrected_text  LONGTEXT     NOT NULL COMMENT '관리자 수정본',
    persona_caution TEXT         NULL     COMMENT '확정된 페르소나 주의사항(단문)',
    admin_id        VARCHAR(32)  NOT NULL COMMENT '처리한 관리자 users.id',
    applied_live    BIT(1)       NOT NULL DEFAULT 0 COMMENT '라이브 글 교체 완료 여부',
    pushed_to_bank  BIT(1)       NOT NULL DEFAULT 0 COMMENT 'example_bank 환류 여부',
    created_at      TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    KEY idx_corr_persona (persona_id),
    KEY idx_corr_target  (target_type, target_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='AI 첨삭 원천 기록';

-- 전역 금지 규칙 (모든 AI 유저 생성 시 프롬프트 주입)
CREATE TABLE ai_global_rules (
    id                   BIGINT PRIMARY KEY AUTO_INCREMENT,
    rule_text            VARCHAR(500) NOT NULL COMMENT '"~하지 말 것" 형식 단문 가이드',
    scope                VARCHAR(16)  NOT NULL DEFAULT 'ALL' COMMENT 'POST | COMMENT | ALL',
    source_correction_id BIGINT       NULL     COMMENT '유래 첨삭 ID (수동 추가 시 NULL 허용)',
    active               BIT(1)       NOT NULL DEFAULT 1,
    created_by           VARCHAR(32)  NOT NULL COMMENT '생성한 관리자 users.id',
    created_at           TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    KEY idx_rule_active  (active),
    CONSTRAINT fk_rule_corr FOREIGN KEY (source_correction_id)
        REFERENCES ai_content_corrections(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='모든 AI 유저 공통 생성 금지 규칙';
