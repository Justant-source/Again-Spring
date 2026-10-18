-- V129: marketing_job.hook_rule — 훅 프롬프트 규칙 버전 태그 (v1/v2 …)
-- 테스트 탭 배지·필터와 A/B 분석(scripts/marketing_ab_report.py)용. NULL = 태그 도입 전(v1).
ALTER TABLE marketing_job
  ADD COLUMN hook_rule VARCHAR(16) NULL
  COMMENT 'Hook prompt rule version (v2 ...). NULL = v1.';
