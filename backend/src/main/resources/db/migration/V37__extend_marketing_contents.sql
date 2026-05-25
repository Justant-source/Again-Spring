-- V37: 마케팅 콘텐츠 테이블 확장 (V15.9)
-- 발행 추적, 성과 수집, 템플릿/리퍼포징 지원 컬럼 추가
-- 플랫폼 ENUM에 threads, facebook 추가 (enabled=false, 활성화는 PR2 YAML 변경)

ALTER TABLE marketing_contents
    ADD COLUMN scheduled_at        TIMESTAMP(3)    NULL COMMENT '예약 발행 시각 (admin이 설정, 자동 발행 안 함)',
    ADD COLUMN published_at        TIMESTAMP(3)    NULL COMMENT '실제 발행 확인 시각 (admin이 수동 기록)',
    ADD COLUMN published_url       VARCHAR(500)    NULL COMMENT '발행 후 URL',
    ADD COLUMN performance_json    JSON            NULL COMMENT '수동 성과 지표 {impressions, likes, comments, shares, clicks, saves, recordedAt}',
    ADD COLUMN template_id         BIGINT          NULL COMMENT '생성에 사용된 콘텐츠 템플릿 ID',
    ADD COLUMN parent_content_id   BIGINT          NULL COMMENT '리퍼포징 직속 부모 콘텐츠 ID',
    ADD COLUMN repurpose_source_id BIGINT          NULL COMMENT '리퍼포징 최초 원본 콘텐츠 ID';

-- 플랫폼 ENUM 확장 (threads, facebook은 YAML에서 enabled=false, 실제 사용은 PR2에서)
ALTER TABLE marketing_contents
    MODIFY COLUMN platform ENUM('x','instagram','naver_blog','threads','facebook')
        NOT NULL COMMENT 'Target social platform';

CREATE INDEX idx_mc_scheduled_at  ON marketing_contents(scheduled_at);
CREATE INDEX idx_mc_published_at  ON marketing_contents(published_at);
