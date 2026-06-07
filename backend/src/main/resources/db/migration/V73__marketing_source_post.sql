-- V73: 마케팅 모듈 개념 전환 — 외부사연/시뮬레이션 제거 → 커뮤니티 게시글 직접 소스
-- (dev 전용 테이블. prod에 marketing 테이블 없음.)

-- 1. marketing_contents: 시뮬레이션 FK/UNIQUE 제거 → source_post_id 추가
ALTER TABLE marketing_contents
    DROP FOREIGN KEY IF EXISTS fk_mc_simulation;

ALTER TABLE marketing_contents
    DROP INDEX IF EXISTS uk_mc_sim_platform;

ALTER TABLE marketing_contents
    DROP COLUMN IF EXISTS simulation_id;

ALTER TABLE marketing_contents
    ADD COLUMN source_post_id VARCHAR(32) NULL COMMENT '홍보 원본 커뮤니티 게시글 ID (posts.id)',
    ADD INDEX idx_mc_source_post (source_post_id);

-- status/platform ENUM 확장 (이미 BIGINT였던 컬럼 제거 후 추가이므로 NULL 허용)
-- platform ENUM은 기존 V30 이후 여러 ALTER로 이미 5종 포함 확인 필요
-- 안전하게 MODIFY로 확장
ALTER TABLE marketing_contents
    MODIFY COLUMN platform ENUM('x','instagram','naver_blog','threads','facebook') NOT NULL;

ALTER TABLE marketing_contents
    MODIFY COLUMN status ENUM(
        'generating','draft','review','approved','exported','rejected',
        'publishing','partial','published','failed'
    ) NOT NULL DEFAULT 'draft';

-- 2. marketing_usage_logs: 시뮬레이션 FK/인덱스 제거
ALTER TABLE marketing_usage_logs
    DROP FOREIGN KEY IF EXISTS fk_mul_simulation;

ALTER TABLE marketing_usage_logs
    DROP INDEX IF EXISTS idx_mul_simulation_id;

ALTER TABLE marketing_usage_logs
    DROP COLUMN IF EXISTS simulation_id;

-- 3. 구 테이블 제거 (dev 전용 데이터 폐기)
DROP TABLE IF EXISTS marketing_simulations;
DROP TABLE IF EXISTS marketing_source_stories;
