-- V22: 페르소나 다양성 v4 — 신원 축(age/gender/marital/job/style) 컬럼 추가
-- (.request/persona-diversity-v4/00-shared.md 계약 1, WP1 작성)

ALTER TABLE personas
    ADD COLUMN age_years      TINYINT      NOT NULL DEFAULT 30   COMMENT '23~49',
    ADD COLUMN gender         CHAR(1)      NOT NULL DEFAULT 'F'  COMMENT 'M|F',
    ADD COLUMN marital        VARCHAR(16)  NOT NULL DEFAULT 'SINGLE' COMMENT 'SINGLE|DATING|ENGAGED|MARRIED',
    ADD COLUMN married_years  TINYINT      NULL                  COMMENT 'MARRIED만. 0~24, <= age_years-25',
    ADD COLUMN has_kids       BIT(1)       NOT NULL DEFAULT 0    COMMENT 'MARRIED만 1 가능',
    ADD COLUMN job_type       VARCHAR(24)  NOT NULL DEFAULT 'CORP_LARGE' COMMENT '8종 쿼터 — 계약 1',
    ADD COLUMN job_title      VARCHAR(80)  NULL                  COMMENT 'LLM 생성 구체 직함',
    ADD COLUMN style_axes     JSON         NULL                  COMMENT '계약 3 — directness/affect/humor/... 10축',
    ADD COLUMN last_post_at   DATETIME(3)  NULL                  COMMENT 'WP3 갱신 — 가중치 계약 6',
    ADD COLUMN last_comment_at DATETIME(3) NULL                  COMMENT 'WP3 갱신 — 가중치 계약 6';

CREATE INDEX idx_personas_last_post_at    ON personas (last_post_at);
CREATE INDEX idx_personas_last_comment_at ON personas (last_comment_at);

-- persona_action_log.action_type은 기존 VARCHAR(16)이라 'PROFILE_REGENERATED'(20자)가 들어가지
-- 않는다. PersonaProfileRegenerator 감사 로그(계약 3 작업)를 위해 폭만 넓힌다(값 집합 변경 없음).
ALTER TABLE persona_action_log
    MODIFY COLUMN action_type VARCHAR(24) NOT NULL COMMENT 'LIKE|VOTE|COMMENT|REPLY|POST|INVITE_ANSWER|PROFILE_REGENERATED';
