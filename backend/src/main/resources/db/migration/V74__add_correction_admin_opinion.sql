-- V74: ai_content_corrections 테이블에 관리자 의견 컬럼 추가
-- 관리자가 첨삭 시 수정 의도·방향을 기록해 일괄 분석의 입력 신호로 활용한다.
ALTER TABLE ai_content_corrections
    ADD COLUMN admin_opinion TEXT NULL COMMENT '관리자가 첨삭 시 남긴 수정 의도/방향(분석 참고용)'
    AFTER corrected_text;
