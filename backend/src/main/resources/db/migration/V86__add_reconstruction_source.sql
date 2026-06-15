-- 재구성 첨삭 전용: 크롤 원본 본문 스냅샷
-- source_original_text != NULL → 재구성 첨삭 (RECONSTRUCTION scope 규칙 생성)
ALTER TABLE ai_content_corrections
    ADD COLUMN IF NOT EXISTS source_original_text LONGTEXT DEFAULT NULL
        COMMENT '재구성 첨삭 원본 본문 스냅샷 (비-null = 재구성 첨삭)';
